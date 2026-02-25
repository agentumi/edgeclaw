/// EdgeClaw error types
#[derive(Debug, thiserror::Error)]
pub enum EdgeClawError {
    #[error("Cryptographic operation failed")]
    CryptoError,

    #[error("Connection failed")]
    ConnectionError,

    #[error("Action denied by policy")]
    PolicyDenied,

    #[error("Invalid capability")]
    InvalidCapability,

    #[error("Session has expired")]
    SessionExpired,

    #[error("Invalid parameter")]
    InvalidParameter,

    #[error("Operation timed out")]
    TimeoutError,

    #[error("Serialization/deserialization error")]
    SerializationError,

    #[error("Internal engine error")]
    InternalError,
}

impl From<serde_json::Error> for EdgeClawError {
    fn from(_: serde_json::Error) -> Self {
        EdgeClawError::SerializationError
    }
}

impl From<aes_gcm::Error> for EdgeClawError {
    fn from(_: aes_gcm::Error) -> Self {
        EdgeClawError::CryptoError
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_error_display() {
        let err = EdgeClawError::CryptoError;
        assert_eq!(format!("{err}"), "Cryptographic operation failed");

        let err = EdgeClawError::PolicyDenied;
        assert_eq!(format!("{err}"), "Action denied by policy");
    }
}
