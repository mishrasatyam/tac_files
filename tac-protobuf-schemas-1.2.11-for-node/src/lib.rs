pub mod tac {
    tonic::include_proto!("tac");

    pub mod events {
        tonic::include_proto!("tac.events");

        pub mod grpc {
            tonic::include_proto!("tac.events.grpc");
        }
    }

    pub mod node {
        pub mod grpc {
            tonic::include_proto!("tac.node.grpc");
        }
    }
}
