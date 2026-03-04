/**
 * Message role constants for chat-style interface
 */
export const MessageRole = {
  USER: 'user',
  ASSISTANT: 'assistant',
  SYSTEM: 'system'
}

/**
 * Create a new message object
 * @param {string} role - Message role (user, assistant, system)
 * @param {string} content - Message content
 * @returns {Object} Message object
 */
export function createMessage(role, content) {
  return {
    id: Date.now() + Math.random(),
    role,
    content,
    timestamp: Date.now()
  }
}
