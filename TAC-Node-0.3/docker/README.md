# Tac Node in Docker

## About Tac
Tac is a decentralized platform that allows any user to issue, transfer, swap and trade custom blockchain tokens on an integrated peer-to-peer exchange. You can find more information about Tac at [tac.tech](https://tac.tech/) and in the official [documentation](https://docs.tac.tech).


## About the image
This Docker image contains scripts and configs to run Tac Node for `mainnet`, 'testnet' or 'stagenet' networks.
The image is focused on fast and convenient deployment of Tac Node.

GitHub repository: https://github.com/tacplatform/Tac/tree/master/docker

## Prerequisites
It is highly recommended to read more about [Tac Node configuration](https://docs.tac.tech/en/tac-node/node-configuration) before running the container.

## Building Docker image
`./build-with-docker.sh && docker build -t tacplatform/tacnode docker` (from the repository root) - builds an image with the current local repository

**You can specify following arguments when building the image:**


|Argument              | Default value |Description   |
|----------------------|-------------------|--------------|
|`TAC_NETWORK`       | `mainnet`         | Tac Blockchain network. Available values are `mainnet`, `testnet`, `stagenet`. Can be overridden in a runtime using environment variable with the same name.|
|`TAC_LOG_LEVEL`     | `DEBUG`           | Default Tac Node log level. Available values: `OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`. More details about logging are available [here](https://docs.tac.tech/en/tac-node/logging-configuration). Can be overridden in a runtime using environment variable with the same name. |
|`TAC_HEAP_SIZE`     | `2g`              | Default Tac Node JVM Heap Size limit in -X Command-line Options notation (`-Xms=[your value]`). More details [here](https://docs.oracle.com/cd/E13150_01/jrockit_jvm/jrockit/jrdocs/refman/optionX.html). Can be overridden in a runtime using environment variable with the same name. |

**Note: All build arguments are optional.**  

## Running Docker image

### Configuration options

1. The image supports Tac Node config customization. To change a config field use corrresponding JVM options. JVM options can be sent to JVM using `JAVA_OPTS` environment variable. Please refer to ([complete configuration file](https://raw.githubusercontent.com/tacplatform/Tac/2634f71899e3100808c44c5ed70b8efdbb600b05/Node/src/main/resources/application.conf)) to get the full path of the configuration item you want to change.

```
docker run -v /docker/tac/tac-data:/var/lib/tac -v /docker/tac/tac-config:/etc/tac -p 6869:6869 -p 6862:6862 -e JAVA_OPTS="-Dtac.rest-api.enable=yes -Dtac.rest-api.bind-address=0.0.0.0 -Dtac.wallet.password=myWalletSuperPassword" -e TAC_NETWORK=stagenet -ti tacplatform/tacnode
```

2. Tac Node is looking for a config in the directory `/etc/tac/tac.conf` which can be mounted using Docker volumes. If this directory does not exist, a default configuration will be copied to this directory. Default configuration is chosen depending on `TAC_NETWORK` environment variable. If the value of `TAC_NETWORK` is not `mainnet`, `testnet` or `stagenet`, default configuration won't be applied. This is a scenario of using `CUSTOM` network - correct configuration must be provided. If you use `CUSTOM` network and `/etc/tac/tac.conf` is NOT found Tac Node container will exit.

3. By default, `/etc/tac/tac.conf` config includes `/etc/tac/local.conf`. Custom `/etc/tac/local.conf` can be used to override default config entries. Custom `/etc/tac/tac.conf` can be used to override or the whole configuration. For additional information about Docker volumes mapping please refer to `Managing data` item.

### Environment variables

**You can run container with predefined environment variables:**

| Env variable                      | Description  |
|-----------------------------------|--------------|
| `TAC_WALLET_SEED`        		| Base58 encoded seed. Overrides `-Dtac.wallet.seed` JVM config option. |
| `TAC_WALLET_PASSWORD`           | Password for the wallet file. Overrides `-Dtac.wallet.password` JVM config option. |
| `TAC_LOG_LEVEL`                 | Node logging level. Available values: `OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`. More details about logging are available [here](https://docs.tac.tech/en/tac-node/logging-configuration).|
| `TAC_HEAP_SIZE`                 | Default Java Heap Size limit in -X Command-line Options notation (`-Xms=[your value]`). More details [here](https://docs.oracle.com/cd/E13150_01/jrockit_jvm/jrockit/jrdocs/refman/optionX.html). |
|`TAC_NETWORK`                    | Tac Blockchain network. Available values are `mainnet`, `testnet`, `stagenet`.|
|`JAVA_OPTS`                        | Additional Tac Node JVM configuration options. 	|

**Note: All variables are optional.**  

**Note: Environment variables override values in the configuration file.** 


### Managing data
We recommend to store the blockchain state as well as Tac configuration on the host side. As such, consider using Docker volumes mapping to map host directories inside the container:

**Example:**

1. Create a directory to store Tac data:

```
mkdir -p /docker/tac
mkdir /docker/tac/tac-data
mkdir /docker/tac/tac-config
```

Once container is launched it will create:

- three subdirectories in `/docker/tac/tac-data`:
```
/docker/tac/tac-data/log    - Tac Node logs
/docker/tac/tac-data/data   - Tac Blockchain state
/docker/tac/tac-data/wallet - Tac Wallet data
```
- `/docker/tac/tac-config/tac.conf - default Tac config


3. If you already have Tac Node configuration/data - place it in the corresponsing directories

4. Add the appropriate arguments to ```docker run``` command: 
```
docker run -v /docker/tac/tac-data:/var/lib/tac -v /docker/tac/tac-config:/etc/tac -e TAC_NETWORK=stagenet -e TAC_WALLET_PASSWORD=myWalletSuperPassword -ti tacplatform/tacnode
```

### Blockchain state

If you are a Tac Blockchain newbie and launching Tac Node for the first time be aware that after launch it will start downloading the whole blockchain state from the other nodes. During this download it will be verifying all blocks one after another. This procesure can take some time.

You can speed this process up by downloading a compressed blockchain state from our official resources, extract it and mount inside the container (as discussed in the previous section). In this scenario Tac Node skips block verifying. This is a reason why it takes less time. This is also a reason why you must download blockchain state *only from our official resources*.

**Note**: We do not guarantee the state consistency if it's downloaded from third-parties.

|Network     |Link          |
|------------|--------------|
|`mainnet`   | http://blockchain.tacnodes.com/blockchain_last.tar |
|`testnet`   | http://blockchain-testnet.tacnodes.com/blockchain_last.tar  |
|`stagenet`  | http://blockchain-stagenet.tacnodes.com/blockchain_last.tar |


**Example:**
```
mkdir -p /docker/tac/tac-data

wget -qO- http://blockchain-stagenet.tacnodes.com/blockchain_last.tar --show-progress | tar -xvf - -C /docker/tac/tac-data

docker run -v /docker/tac/tac-data:/var/lib/tac tacplatform/Node -e TAC_NETWORK=stagenet -e TAC_WALLET_PASSWORD=myWalletSuperPassword -ti tacplatform/tacnode
```

### Network Ports

1. REST-API interaction with Node. Details are available [here](https://docs.tac.tech/en/tac-node/node-configuration#rest-api-settings).

2. Tac Node communication port for incoming connections. Details are available [here](https://docs.tac.tech/en/tac-node/node-configuration#network-settings).


**Example:**
Below command will launch a container:
- with REST-API port enabled and configured on the socket `0.0.0.0:6870`
- Tac node communication port enabled and configured on the socket `0.0.0.0:6868`
- Ports `6868` and `6870` mapped from the host to the container

```
docker run -v /docker/tac/tac-data:/var/lib/tac -v /docker/tac/tac-config:/etc/tac -p 6870:6870 -p 6868:6868 -e JAVA_OPTS="-Dtac.network.declared-address=0.0.0.0:6868 -Dtac.rest-api.port=6870 -Dtac.rest-api.bind-address=0.0.0.0 -Dtac.rest-api.enable=yes" -e TAC_WALLET_PASSWORD=myWalletSuperPassword -e TAC_NETWORK=stagenet -ti tacplatform/tacnode
```

Check that REST API is up by navigating to the following URL from the host side:
http://localhost:6870/api-docs/index.html

### Extensions
You can run custom extensions in this way:
1. Copy all lib/*.jar files from extension to any directory, lets say `plugins`
2. Add extension class to configuration file, lets say `local.conf`:
```hocon
tac.extensions += com.johndoe.TacExtension
```
3. Run `docker run -v "$(pwd)/plugins:/usr/share/tac/lib/plugins" -v "$(pwd)/local.conf:/etc/tac/local.conf" -i tacplatform/tacnode`
