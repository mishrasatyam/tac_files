fn main() -> Result<(), Box<dyn std::error::Error>> {
    tonic_build::configure().compile(
        &[
            "proto/tac/node/grpc/accounts_api.proto",
            "proto/tac/node/grpc/assets_api.proto",
            "proto/tac/node/grpc/blockchain_api.proto",
            "proto/tac/node/grpc/blocks_api.proto",
            "proto/tac/node/grpc/transactions_api.proto",
            "proto/tac/events/events.proto",
            "proto/tac/events/grpc/blockchain_updates.proto",
        ],
        &["proto"],
    )?;

    Ok(())
}
