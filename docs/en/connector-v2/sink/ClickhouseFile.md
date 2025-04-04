import ChangeLog from '../changelog/connector-clickhouse.md';

# ClickhouseFile

> Clickhouse file sink connector

## Description

Generate the clickhouse data file with the clickhouse-local program, and then send it to the clickhouse
server, also call bulk load. This connector only support clickhouse table which engine is 'Distributed'.And `internal_replication` option
should be `true`. Supports Batch and Streaming mode.

## Key features

- [ ] [exactly-once](../../concept/connector-v2-features.md)

:::tip

Write data to Clickhouse can also be done using JDBC

:::

## Options

|          Name          |  Type   | Required |                Default                 |
|------------------------|---------|----------|----------------------------------------|
| host                   | string  | yes      | -                                      |
| database               | string  | yes      | -                                      |
| table                  | string  | yes      | -                                      |
| username               | string  | yes      | -                                      |
| password               | string  | yes      | -                                      |
| clickhouse_local_path  | string  | yes      | -                                      |
| sharding_key           | string  | no       | -                                      |
| copy_method            | string  | no       | scp                                    |
| node_free_password     | boolean | no       | false                                  |
| node_pass              | list    | no       | -                                      |
| node_pass.node_address | string  | no       | -                                      |
| node_pass.username     | string  | no       | "root"                                 |
| node_pass.password     | string  | no       | -                                      |
| compatible_mode        | boolean | no       | false                                  |
| file_fields_delimiter  | string  | no       | "\t"                                   |
| file_temp_path         | string  | no       | "/tmp/seatunnel/clickhouse-local/file" |
| key_path               | string  | no       | "/tmp/id_rsa"                          |
| common-options         |         | no       | -                                      |

### host [string]

`ClickHouse` cluster address, the format is `host:port` , allowing multiple `hosts` to be specified. Such as `"host1:8123,host2:8123"` .

### database [string]

The `ClickHouse` database

### table [string]

The table name

### username [string]

`ClickHouse` user username

### password [string]

`ClickHouse` user password

### sharding_key [string]

When ClickhouseFile split data, which node to send data to is a problem, the default is random selection, but the
'sharding_key' parameter can be used to specify the field for the sharding algorithm.

### clickhouse_local_path [string]

The address of the clickhouse-local program on the spark node. Since each task needs to be called,
clickhouse-local should be located in the same path of each spark node.

### copy_method [string]

Specifies the method used to transfer files, the default is scp, optional scp and rsync

### node_free_password [boolean]

Because seatunnel need to use scp or rsync for file transfer, seatunnel need clickhouse server-side access.
If each spark node and clickhouse server are configured with password-free login,
you can configure this option to true, otherwise you need to configure the corresponding node password in the node_pass configuration

### node_pass [list]

Used to save the addresses and corresponding passwords of all clickhouse servers

### node_pass.node_address [string]

The address corresponding to the clickhouse server

### node_pass.username [string]

The username corresponding to the clickhouse server, default root user.

### node_pass.password [string]

The password corresponding to the clickhouse server.

### compatible_mode [boolean]

In the lower version of Clickhouse, the ClickhouseLocal program does not support the `--path` parameter,
you need to use this mode to take other ways to realize the `--path` parameter function

### file_fields_delimiter [string]

ClickhouseFile uses csv format to temporarily save data. If the data in the row contains the delimiter value
of csv, it may cause program exceptions.
Avoid this with this configuration. Value string has to be an exactly one character long

### file_temp_path [string]

The directory where ClickhouseFile stores temporary files locally.

### key_path [string]

The path of the private key file used for scp or rsync to connect to the ClickHouse server.

### common options

Sink plugin common parameters, please refer to [Sink Common Options](../sink-common-options.md) for details

## Examples

```hocon
ClickhouseFile {
  host = "192.168.0.1:8123"
  database = "default"
  table = "fake_all"
  username = "default"
  password = ""
  clickhouse_local_path = "/Users/seatunnel/Tool/clickhouse local"
  sharding_key = "age"
  node_free_password = false
  node_pass = [{
    node_address = "192.168.0.1"
    password = "seatunnel"
  }]
}
```

## Changelog

<ChangeLog />


