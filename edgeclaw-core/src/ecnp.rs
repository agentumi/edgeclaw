use crate::error::EdgeClawError;
use crate::protocol::MessageType;

/// ECNP v1.1 frame format:
/// ┌──────────┬──────────┬──────────────┬──────────────┐
/// │ Version  │  Type    │   Length     │   Payload    │
/// │  1 byte  │  1 byte  │   4 bytes   │   N bytes    │
/// └──────────┴──────────┴──────────────┴──────────────┘
const ECNP_VERSION: u8 = 0x01;
const HEADER_SIZE: usize = 6; // 1 + 1 + 4
const MAX_PAYLOAD_SIZE: usize = 1024 * 1024; // 1 MB max

/// ECNP message exposed via UniFFI
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct EcnpMessage {
    pub version: u8,
    pub msg_type: u8,
    pub payload: Vec<u8>,
}

/// ECNP v1.1 codec — binary framing for edge protocol
pub struct EcnpCodec;

impl EcnpCodec {
    /// Encode a message into ECNP v1.1 wire format
    pub fn encode(msg_type: MessageType, payload: &[u8]) -> Result<Vec<u8>, EdgeClawError> {
        if payload.len() > MAX_PAYLOAD_SIZE {
            return Err(EdgeClawError::InvalidParameter);
        }

        let length = payload.len() as u32;
        let mut frame = Vec::with_capacity(HEADER_SIZE + payload.len());

        frame.push(ECNP_VERSION);
        frame.push(msg_type as u8);
        frame.extend_from_slice(&length.to_be_bytes());
        frame.extend_from_slice(payload);

        Ok(frame)
    }

    /// Decode a message from ECNP v1.1 wire format
    pub fn decode(data: &[u8]) -> Result<EcnpMessage, EdgeClawError> {
        if data.len() < HEADER_SIZE {
            return Err(EdgeClawError::InvalidParameter);
        }

        let version = data[0];
        if version != ECNP_VERSION {
            return Err(EdgeClawError::InvalidParameter);
        }

        let msg_type = data[1];
        // Validate message type
        let _ = MessageType::try_from(msg_type)?;

        let length = u32::from_be_bytes([data[2], data[3], data[4], data[5]]) as usize;

        if length > MAX_PAYLOAD_SIZE {
            return Err(EdgeClawError::InvalidParameter);
        }

        if data.len() < HEADER_SIZE + length {
            return Err(EdgeClawError::InvalidParameter);
        }

        let payload = data[HEADER_SIZE..HEADER_SIZE + length].to_vec();

        Ok(EcnpMessage {
            version,
            msg_type,
            payload,
        })
    }

    /// Encode a string payload with the given message type
    pub fn encode_string(msg_type: MessageType, text: &str) -> Result<Vec<u8>, EdgeClawError> {
        Self::encode(msg_type, text.as_bytes())
    }

    /// Decode and return payload as string
    pub fn decode_string(data: &[u8]) -> Result<(u8, String), EdgeClawError> {
        let msg = Self::decode(data)?;
        let text = String::from_utf8(msg.payload).map_err(|_| EdgeClawError::SerializationError)?;
        Ok((msg.msg_type, text))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::protocol::MessageType;

    #[test]
    fn test_encode_decode_roundtrip() {
        let payload = b"Hello ECNP!";
        let frame = EcnpCodec::encode(MessageType::Data, payload).unwrap();

        assert_eq!(frame[0], ECNP_VERSION);
        assert_eq!(frame[1], MessageType::Data as u8);
        assert_eq!(frame.len(), HEADER_SIZE + payload.len());

        let msg = EcnpCodec::decode(&frame).unwrap();
        assert_eq!(msg.version, ECNP_VERSION);
        assert_eq!(msg.msg_type, MessageType::Data as u8);
        assert_eq!(msg.payload, payload);
    }

    #[test]
    fn test_encode_string_roundtrip() {
        let text = "EdgeClaw heartbeat";
        let frame = EcnpCodec::encode_string(MessageType::Heartbeat, text).unwrap();

        let (msg_type, decoded) = EcnpCodec::decode_string(&frame).unwrap();
        assert_eq!(msg_type, MessageType::Heartbeat as u8);
        assert_eq!(decoded, text);
    }

    #[test]
    fn test_decode_too_short() {
        assert!(EcnpCodec::decode(&[0x01, 0x02]).is_err());
    }

    #[test]
    fn test_decode_wrong_version() {
        let frame = vec![0xFF, 0x01, 0x00, 0x00, 0x00, 0x00];
        assert!(EcnpCodec::decode(&frame).is_err());
    }

    #[test]
    fn test_decode_invalid_type() {
        let frame = vec![0x01, 0xFF, 0x00, 0x00, 0x00, 0x00];
        assert!(EcnpCodec::decode(&frame).is_err());
    }

    #[test]
    fn test_decode_length_mismatch() {
        // Header says 10 bytes but only 2 are provided
        let frame = vec![0x01, 0x02, 0x00, 0x00, 0x00, 0x0A, 0xAA, 0xBB];
        assert!(EcnpCodec::decode(&frame).is_err());
    }

    #[test]
    fn test_empty_payload() {
        let frame = EcnpCodec::encode(MessageType::Ack, &[]).unwrap();
        let msg = EcnpCodec::decode(&frame).unwrap();
        assert!(msg.payload.is_empty());
        assert_eq!(msg.msg_type, MessageType::Ack as u8);
    }

    #[test]
    fn test_all_message_types() {
        let types = vec![
            MessageType::Handshake,
            MessageType::Data,
            MessageType::Control,
            MessageType::Heartbeat,
            MessageType::Ack,
            MessageType::Error,
        ];

        for mt in types {
            let frame = EcnpCodec::encode(mt, b"test").unwrap();
            let msg = EcnpCodec::decode(&frame).unwrap();
            assert_eq!(msg.msg_type, mt as u8);
        }
    }
}
