/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.file.ftp.system;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileAlreadyExistsException;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.ParentNotDirectoryException;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.ftp.FTPException;
import org.apache.hadoop.fs.ftp.FTPInputStream;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.util.Progressable;

import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;

/**
 * A {@link FileSystem} backed by an FTP client provided by <a
 * href="http://commons.apache.org/net/">Apache Commons Net</a>.
 */
@InterfaceAudience.Public
@InterfaceStability.Stable
@Slf4j
public class SeaTunnelFTPFileSystem extends FileSystem {
    public static final Log LOG = LogFactory.getLog(SeaTunnelFTPFileSystem.class);

    public static final int DEFAULT_BUFFER_SIZE = 1024 * 1024;

    public static final int DEFAULT_BLOCK_SIZE = 4 * 1024;
    public static final String FS_FTP_USER_PREFIX = "fs.ftp.user.";
    public static final String FS_FTP_HOST = "fs.ftp.host";
    public static final String FS_FTP_HOST_PORT = "fs.ftp.host.port";
    public static final String FS_FTP_PASSWORD_PREFIX = "fs.ftp.password.";
    public static final String FS_FTP_CONNECTION_MODE = "fs.ftp.connection.mode";

    public static final String E_SAME_DIRECTORY_ONLY = "only same directory renames are supported";

    private URI uri;

    /**
     * Return the protocol scheme for the FileSystem.
     *
     * <p>
     *
     * @return <code>ftp</code>
     */
    @Override
    public String getScheme() {
        return "ftp";
    }

    @Override
    public void initialize(URI uri, Configuration conf) throws IOException { // get
        super.initialize(uri, conf);
        // get host information from uri (overrides info in conf)
        String host = uri.getHost();
        host = (host == null) ? conf.get(FS_FTP_HOST, null) : host;
        if (host == null) {
            throw new IOException("Invalid host specified");
        }
        conf.set(FS_FTP_HOST, host);

        // get port information from uri, (overrides info in conf)
        int port = uri.getPort();
        port = (port == -1) ? FTP.DEFAULT_PORT : port;
        conf.setInt("fs.ftp.host.port", port);

        // get user/password information from URI (overrides info in conf)
        String userAndPassword = uri.getUserInfo();
        if (userAndPassword == null) {
            String user = conf.get("fs.ftp.user." + host, null);
            String password = conf.get("fs.ftp.password." + host, null);
            if (user == null || password == null) {
                throw new IOException("Invalid user/password specified");
            }
            userAndPassword = user + ":" + password;
        }
        String[] userPasswdInfo = userAndPassword.split(":");
        conf.set(FS_FTP_USER_PREFIX + host, userPasswdInfo[0]);
        if (userPasswdInfo.length > 1) {
            conf.set(FS_FTP_PASSWORD_PREFIX + host, userPasswdInfo[1]);
        } else {
            conf.set(FS_FTP_PASSWORD_PREFIX + host, null);
        }
        setConf(conf);
        this.uri = uri;
    }

    /**
     * Connect to the FTP server using configuration parameters *
     *
     * @return An FTPClient instance
     * @throws IOException IOException
     */
    private FTPClient connect() throws IOException {
        FTPClient client = new FTPClient();
        Configuration conf = getConf();
        // Get the connection mode from configuration, default to passive_local mode
        String connectionMode =
                conf.get(FS_FTP_CONNECTION_MODE, FtpConnectionMode.ACTIVE_LOCAL.getMode());

        // Retrieve host, port, user, and password from configuration
        String host = conf.get(FS_FTP_HOST);
        int port = conf.getInt(FS_FTP_HOST_PORT, FTP.DEFAULT_PORT);
        String user = conf.get(FS_FTP_USER_PREFIX + host);
        String password = conf.get(FS_FTP_PASSWORD_PREFIX + host);

        // Connect to the FTP server
        client.connect(host, port);
        int reply = client.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            throw NetUtils.wrapException(
                    host,
                    port,
                    NetUtils.UNKNOWN_HOST,
                    0,
                    new ConnectException("Server response " + reply));
        }

        // Log in to the FTP server
        if (!client.login(user, password)) {
            throw new IOException(
                    String.format(
                            "Login failed on server - %s, port - %d as user '%s', reply code: %d",
                            host, port, user, client.getReplyCode()));
        }

        // Set the file type to binary and buffer size
        client.setFileType(FTP.BINARY_FILE_TYPE);
        client.setBufferSize(DEFAULT_BUFFER_SIZE);
        client.setFileTransferMode(FTP.BLOCK_TRANSFER_MODE);

        // Set the connection mode
        setFsFtpConnectionMode(client, connectionMode);

        // Log successful connection information
        LOG.info(
                String.format(
                        "Successfully connected to FTP server %s:%d in %s",
                        host, port, connectionMode));

        return client;
    }

    /**
     * Set FTP connection mode. *
     *
     * @param client FTPClient
     * @param mode mode
     */
    private void setFsFtpConnectionMode(FTPClient client, String mode) throws IOException {
        FtpConnectionMode connectionMode = FtpConnectionMode.fromMode(mode);
        switch (connectionMode) {
            case PASSIVE_LOCAL:
                client.enterLocalPassiveMode();
                LOG.info("Using passive mode for FTP connection");
                break;
            case ACTIVE_LOCAL:
                // Create a test directory to check if active mode is working
                String pathName = "/.ftptest" + System.currentTimeMillis();
                try {
                    client.enterLocalActiveMode();
                    // test active mode is working or not
                    boolean created = client.makeDirectory(pathName);
                    if (!created) {
                        LOG.warn("Active mode failed, switching to passive mode");
                        throw new IOException("FTP connection active mode test failed");
                    }

                    LOG.info("Using active mode for FTP connection");
                } catch (IOException e) {
                    // if active mode failed, switch to passive mode
                    client.enterLocalPassiveMode();
                    // update the connection mode to passive mode
                    getConf()
                            .set(FS_FTP_CONNECTION_MODE, FtpConnectionMode.PASSIVE_LOCAL.getMode());
                } finally {
                    // delete the test directory if it was created
                    FTPFile[] files = client.listFiles(pathName);
                    if (files != null && files.length > 0) {
                        client.deleteFile(pathName);
                    }
                }
                break;
            default:
                log.warn(
                        "Unsupported FTP connection mode: " + mode,
                        " Using default FTP connection mode: "
                                + FtpConnectionMode.ACTIVE_LOCAL.getMode());
                client.enterLocalActiveMode();
                break;
        }
    }

    /**
     * Logout and disconnect the given FTPClient. *
     *
     * @param client FTPClient
     * @throws IOException IOException
     */
    private void disconnect(FTPClient client) throws IOException {
        if (client != null) {
            if (!client.isConnected()) {
                throw new FTPException("Client not connected");
            }
            boolean logoutSuccess = client.logout();
            client.disconnect();
            if (!logoutSuccess) {
                LOG.warn(
                        "Logout failed while disconnecting, error code - " + client.getReplyCode());
            }
        }
    }

    /**
     * Resolve against given working directory. *
     *
     * @param workDir workDir
     * @param path path
     * @return Path
     */
    private Path makeAbsolute(Path workDir, Path path) {
        if (path.isAbsolute()) {
            return path;
        }
        return new Path(workDir, path);
    }

    @Override
    public FSDataInputStream open(Path file, int bufferSize) throws IOException {
        FTPClient client = connect();
        Path workDir = new Path(client.printWorkingDirectory());
        Path absolute = makeAbsolute(workDir, file);
        FileStatus fileStat = getFileStatus(client, absolute);
        if (fileStat.isDirectory()) {
            disconnect(client);
            throw new FileNotFoundException("Path " + file + " is a directory.");
        }
        client.allocate(bufferSize);
        Path parent = absolute.getParent();
        // Change to parent directory on the
        // server. Only then can we read the
        // file
        // on the server by opening up an InputStream. As a side effect the working
        // directory on the server is changed to the parent directory of the file.
        // The FTP client connection is closed when close() is called on the
        // FSDataInputStream.
        client.changeWorkingDirectory(parent.toUri().getPath());
        InputStream is = client.retrieveFileStream(file.getName());
        FSDataInputStream fis = new FSDataInputStream(new FTPInputStream(is, client, statistics));
        if (!FTPReply.isPositivePreliminary(client.getReplyCode())) {
            // The ftpClient is an inconsistent state. Must close the stream
            // which in turn will logout and disconnect from FTP server
            fis.close();
            throw new IOException("Unable to open file: " + file + ", Aborting");
        }
        return fis;
    }

    /**
     * A stream obtained via this call must be closed before using other APIs of this class or else
     * the invocation will block.
     */
    @Override
    public FSDataOutputStream create(
            Path file,
            FsPermission permission,
            boolean overwrite,
            int bufferSize,
            short replication,
            long blockSize,
            Progressable progress)
            throws IOException {
        final FTPClient client = connect();
        Path workDir = new Path(client.printWorkingDirectory());
        Path absolute = makeAbsolute(workDir, file);
        FileStatus status;
        try {
            status = getFileStatus(client, file);
        } catch (FileNotFoundException fnfe) {
            status = null;
        }
        if (status != null) {
            if (overwrite && !status.isDirectory()) {
                delete(client, file, false);
            } else {
                disconnect(client);
                throw new FileAlreadyExistsException("File already exists: " + file);
            }
        }

        Path parent = absolute.getParent();
        if (parent == null || !mkdirs(client, parent, FsPermission.getDirDefault())) {
            parent = (parent == null) ? new Path("/") : parent;
            disconnect(client);
            throw new IOException("create(): Mkdirs failed to create: " + parent);
        }
        client.allocate(bufferSize);
        // Change to parent directory on the server. Only then can we write to the
        // file on the server by opening up an OutputStream. As a side effect the
        // working directory on the server is changed to the parent directory of the
        // file. The FTP client connection is closed when close() is called on the
        // FSDataOutputStream.
        client.changeWorkingDirectory(parent.toUri().getPath());
        FSDataOutputStream fos =
                new FSDataOutputStream(client.storeFileStream(file.getName()), statistics) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        if (!client.isConnected()) {
                            throw new FTPException("Client not connected");
                        }
                        boolean cmdCompleted = client.completePendingCommand();
                        disconnect(client);
                        if (!cmdCompleted) {
                            throw new FTPException(
                                    "Could not complete transfer, Reply Code - "
                                            + client.getReplyCode());
                        }
                    }
                };
        if (!FTPReply.isPositivePreliminary(client.getReplyCode())) {
            // The ftpClient is an inconsistent state. Must close the stream
            // which in turn will logout and disconnect from FTP server
            fos.close();
            throw new IOException("Unable to create file: " + file + ", Aborting");
        }
        return fos;
    }

    /** This optional operation is not yet supported. */
    @Override
    public FSDataOutputStream append(Path f, int bufferSize, Progressable progress)
            throws IOException {
        throw new IOException("Not supported");
    }

    /**
     * Convenience method, so that we don't open a new connection when using this method from within
     * another method. Otherwise every API invocation incurs the overhead of opening/closing a TCP
     * connection.
     *
     * @throws IOException on IO problems other than FileNotFoundException
     */
    private boolean exists(FTPClient client, Path file) throws IOException {
        try {
            return getFileStatus(client, file) != null;
        } catch (FileNotFoundException fnfe) {
            LOG.debug("File does not exist: " + file, fnfe);
            return false;
        }
    }

    @Override
    public boolean delete(Path file, boolean recursive) throws IOException {
        FTPClient client = connect();
        try {
            boolean success = delete(client, file, recursive);
            return success;
        } finally {
            disconnect(client);
        }
    }

    /**
     * Convenience method, so that we don't open a new connection when using this method from within
     * another method. Otherwise every API invocation incurs the overhead of opening/closing a TCP
     * connection.
     */
    private boolean delete(FTPClient client, Path file, boolean recursive) throws IOException {
        Path workDir = new Path(client.printWorkingDirectory());
        Path absolute = makeAbsolute(workDir, file);
        String pathName = absolute.toUri().getPath();
        try {
            FileStatus fileStat = getFileStatus(client, absolute);
            if (fileStat.isFile()) {
                return client.deleteFile(pathName);
            }
        } catch (FileNotFoundException e) {
            // the file is not there
            return false;
        }
        FileStatus[] dirEntries = listStatus(client, absolute);
        if (dirEntries != null && dirEntries.length > 0 && !recursive) {
            throw new IOException("Directory: " + file + " is not empty.");
        }
        if (dirEntries != null) {
            for (int i = 0; i < dirEntries.length; i++) {
                delete(client, new Path(absolute, dirEntries[i].getPath()), recursive);
            }
        }
        return client.removeDirectory(pathName);
    }

    private FsAction getFsAction(int accessGroup, FTPFile ftpFile) {
        FsAction action = FsAction.NONE;
        if (ftpFile.hasPermission(accessGroup, FTPFile.READ_PERMISSION)) {
            action.or(FsAction.READ);
        }
        if (ftpFile.hasPermission(accessGroup, FTPFile.WRITE_PERMISSION)) {
            action.or(FsAction.WRITE);
        }
        if (ftpFile.hasPermission(accessGroup, FTPFile.EXECUTE_PERMISSION)) {
            action.or(FsAction.EXECUTE);
        }
        return action;
    }

    private FsPermission getPermissions(FTPFile ftpFile) {
        FsAction user;
        FsAction group;
        FsAction others;
        user = getFsAction(FTPFile.USER_ACCESS, ftpFile);
        group = getFsAction(FTPFile.GROUP_ACCESS, ftpFile);
        others = getFsAction(FTPFile.WORLD_ACCESS, ftpFile);
        return new FsPermission(user, group, others);
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public FileStatus[] listStatus(Path file) throws IOException {
        FTPClient client = connect();
        try {
            FileStatus[] stats = listStatus(client, file);
            return stats;
        } finally {
            disconnect(client);
        }
    }

    /**
     * Convenience method, so that we don't open a new connection when using this method from within
     * another method. Otherwise every API invocation incurs the overhead of opening/closing a TCP
     * connection.
     */
    private FileStatus[] listStatus(FTPClient client, Path file) throws IOException {
        Path workDir = new Path(client.printWorkingDirectory());
        Path absolute = makeAbsolute(workDir, file);
        FileStatus fileStat = getFileStatus(client, absolute);
        if (fileStat.isFile()) {
            return new FileStatus[] {fileStat};
        }
        FTPFile[] ftpFiles = client.listFiles(absolute.toUri().getPath());
        FileStatus[] fileStats = new FileStatus[ftpFiles.length];
        for (int i = 0; i < ftpFiles.length; i++) {
            fileStats[i] = getFileStatus(ftpFiles[i], absolute);
        }
        return fileStats;
    }

    @Override
    public FileStatus getFileStatus(Path file) throws IOException {
        FTPClient client = connect();
        try {
            FileStatus status = getFileStatus(client, file);
            return status;
        } finally {
            disconnect(client);
        }
    }

    /**
     * Convenience method, so that we don't open a new connection when using this method from within
     * another method. Otherwise every API invocation incurs the overhead of opening/closing a TCP
     * connection.
     */
    private FileStatus getFileStatus(FTPClient client, Path file) throws IOException {
        FileStatus fileStat = null;
        Path workDir = new Path(client.printWorkingDirectory());
        Path absolute = makeAbsolute(workDir, file);
        Path parentPath = absolute.getParent();
        if (parentPath == null) { // root dir
            long length = -1; // Length of root dir on server not known
            boolean isDir = true;
            int blockReplication = 1;
            long blockSize = DEFAULT_BLOCK_SIZE; // Block Size not known.
            long modTime = -1; // Modification time of root dir not known.
            Path root = new Path("/");
            return new FileStatus(
                    length, isDir, blockReplication, blockSize, modTime, root.makeQualified(this));
        }
        String pathName = parentPath.toUri().getPath();
        FTPFile[] ftpFiles = client.listFiles(pathName);
        if (ftpFiles != null) {
            for (FTPFile ftpFile : ftpFiles) {
                if (ftpFile.getName().equals(file.getName())) { // file found in dir
                    fileStat = getFileStatus(ftpFile, parentPath);
                    break;
                }
            }
            if (fileStat == null) {
                throw new FileNotFoundException("File " + file + " does not exist.");
            }
        } else {
            throw new FileNotFoundException("File " + file + " does not exist.");
        }
        return fileStat;
    }

    /**
     * Convert the file information in FTPFile to a {@link FileStatus} object. *
     *
     * @param ftpFile ftpFile
     * @param parentPath parent path
     * @return FileStatus
     */
    private FileStatus getFileStatus(FTPFile ftpFile, Path parentPath) {
        long length = ftpFile.getSize();
        boolean isDir = ftpFile.isDirectory();
        int blockReplication = 1;
        // Using default block size since there is no way in FTP client to know of
        // block sizes on server. The assumption could be less than ideal.
        long blockSize = DEFAULT_BLOCK_SIZE;
        long modTime = ftpFile.getTimestamp().getTimeInMillis();
        long accessTime = 0;
        FsPermission permission = getPermissions(ftpFile);
        String user = ftpFile.getUser();
        String group = ftpFile.getGroup();
        Path filePath = new Path(parentPath, ftpFile.getName());
        return new FileStatus(
                length,
                isDir,
                blockReplication,
                blockSize,
                modTime,
                accessTime,
                permission,
                user,
                group,
                filePath.makeQualified(this));
    }

    @Override
    public boolean mkdirs(Path file, FsPermission permission) throws IOException {
        FTPClient client = connect();
        try {
            boolean success = mkdirs(client, file, permission);
            return success;
        } finally {
            disconnect(client);
        }
    }

    /**
     * Convenience method, so that we don't open a new connection when using this method from within
     * another method. Otherwise every API invocation incurs the overhead of opening/closing a TCP
     * connection.
     */
    private boolean mkdirs(FTPClient client, Path file, FsPermission permission)
            throws IOException {
        Path workDir = new Path(client.printWorkingDirectory());
        Path absolute = makeAbsolute(workDir, file);
        // If directory already exists, return true
        if (exists(client, absolute)) {
            if (isFile(client, absolute)) {
                throw new ParentNotDirectoryException(
                        String.format(
                                "Can't make directory for path %s since it is a file.", absolute));
            }
            return true;
        }

        // Create parent directories if they don't exist
        Path parent = absolute.getParent();
        if (parent != null && !exists(client, parent)) {
            mkdirs(client, parent, FsPermission.getDirDefault());
        }

        // Create the directory
        String pathName = absolute.getName();
        String parentDir = parent != null ? parent.toUri().getPath() : "/";

        // Change to parent directory
        if (!client.changeWorkingDirectory(parentDir)) {
            throw new IOException(
                    String.format(
                            "Failed to change working directory to %s, FTP reply code: %d, reply string: %s",
                            parentDir, client.getReplyCode(), client.getReplyString()));
        }
        // Create directory
        boolean created = client.makeDirectory(pathName);
        if (!created) {
            // Double check if directory was actually created (some FTP servers don't return true)
            if (!exists(client, absolute)) {
                throw new IOException(
                        String.format(
                                "Failed to create directory %s in %s, FTP reply code: %d, reply string: %s",
                                pathName,
                                parentDir,
                                client.getReplyCode(),
                                client.getReplyString()));
            }
        }
        return true;
    }

    /**
     * Convenience method, so that we don't open a new connection when using this method from within
     * another method. Otherwise every API invocation incurs the overhead of opening/closing a TCP
     * connection.
     */
    private boolean isFile(FTPClient client, Path file) {
        try {
            return getFileStatus(client, file).isFile();
        } catch (FileNotFoundException e) {
            return false; // file does not exist
        } catch (IOException ioe) {
            throw new FTPException("File check failed", ioe);
        }
    }

    /*
     * Assuming that parent of both source and destination is the same. Is the
     * assumption correct or it is supposed to work like 'move' ?
     */
    @Override
    public boolean rename(Path src, Path dst) throws IOException {
        FTPClient client = connect();
        try {
            boolean success = rename(client, src, dst);
            return success;
        } finally {
            disconnect(client);
        }
    }

    /**
     * Probe for a path being a parent of another
     *
     * @param parent parent path
     * @param child possible child path
     * @return true if the parent's path matches the start of the child's
     */
    private boolean isParentOf(Path parent, Path child) {
        URI parentURI = parent.toUri();
        String parentPath = parentURI.getPath();
        if (!parentPath.endsWith("/")) {
            parentPath += "/";
        }
        URI childURI = child.toUri();
        String childPath = childURI.getPath();
        return childPath.startsWith(parentPath);
    }

    /**
     * Convenience method, so that we don't open a new connection when using this method from within
     * another method. Otherwise every API invocation incurs the overhead of opening/closing a TCP
     * connection.
     *
     * @param client FTPClient
     * @param src src
     * @param dst dst
     * @return result
     * @throws IOException IOException
     */
    private boolean rename(FTPClient client, Path src, Path dst) throws IOException {
        Path workDir = new Path(client.printWorkingDirectory());
        Path absoluteSrc = makeAbsolute(workDir, src);
        Path absoluteDst = makeAbsolute(workDir, dst);
        if (!exists(client, absoluteSrc)) {
            throw new FileNotFoundException("Source path " + src + " does not exist");
        }
        if (isDirectory(absoluteDst)) {
            // destination is a directory: rename goes underneath it with the
            // source name
            absoluteDst = new Path(absoluteDst, absoluteSrc.getName());
        }
        if (exists(client, absoluteDst)) {
            throw new FileAlreadyExistsException("Destination path " + dst + " already exists");
        }
        if (isParentOf(absoluteSrc, absoluteDst)) {
            throw new IOException(
                    "Cannot rename " + absoluteSrc + " under itself" + " : " + absoluteDst);
        }
        String from = absoluteSrc.toString();
        String to = absoluteDst.toString();
        return client.rename(from, to);
    }

    @Override
    public Path getWorkingDirectory() {
        // Return home directory always since we do not maintain state.
        return getHomeDirectory();
    }

    @Override
    public Path getHomeDirectory() {
        FTPClient client = null;
        try {
            client = connect();
            Path homeDir = new Path(client.printWorkingDirectory());
            return homeDir;
        } catch (IOException ioe) {
            throw new FTPException("Failed to get home directory", ioe);
        } finally {
            try {
                disconnect(client);
            } catch (IOException ioe) {
                throw new FTPException("Failed to disconnect", ioe);
            }
        }
    }

    @Override
    public void setWorkingDirectory(Path newDir) {
        // we do not maintain the working directory state
    }
}
