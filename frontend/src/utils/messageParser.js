import { MessageRole, createMessage } from '../types/chat'

/**
 * Parse terminal output text into chat messages
 * Lines starting with "> " are user input, others are assistant output
 * @param {string} outputText - Raw terminal output text
 * @returns {Array} Array of message objects
 */
export function parseOutputToMessages(outputText) {
  if (!outputText || !outputText.trim()) {
    return []
  }

  const lines = outputText.split('\n').filter(line => line.trim())
  const messages = []
  let currentMessage = null

  for (const line of lines) {
    const trimmedLine = line.trim()

    // Check if this is a user input line (starts with "> ")
    if (trimmedLine.startsWith('> ')) {
      // Save previous message if exists
      if (currentMessage) {
        messages.push(currentMessage)
      }
      // Start new user message
      currentMessage = createMessage(MessageRole.USER, trimmedLine.substring(2))
    } else {
      // This is assistant output
      if (currentMessage && currentMessage.role === MessageRole.ASSISTANT) {
        // Append to existing assistant message
        currentMessage.content += '\n' + trimmedLine
      } else {
        // Save previous message if exists
        if (currentMessage) {
          messages.push(currentMessage)
        }
        // Start new assistant message
        currentMessage = createMessage(MessageRole.ASSISTANT, trimmedLine)
      }
    }
  }

  // Don't forget the last message
  if (currentMessage) {
    messages.push(currentMessage)
  }

  return messages
}

/**
 * Convert messages array back to storage format
 * @param {Array} messages - Array of message objects
 * @returns {string} Terminal-style output text
 */
export function messagesToOutput(messages) {
  if (!messages || messages.length === 0) {
    return ''
  }

  return messages.map(m => {
    if (m.role === MessageRole.USER) {
      return `> ${m.content}`
    }
    return m.content
  }).join('\n')
}

/**
 * Parse WebSocket output data into message format
 * @param {Object} data - WebSocket data object with type, stream, data/content
 * @returns {Object|null} Message object or null if not a message
 */
export function parseWebSocketData(data) {
  if (!data) return null

  // Handle new message format with role field
  if (data.type === 'message' && data.role) {
    return {
      id: data.timestamp || Date.now() + Math.random(),
      role: data.role,
      content: data.content || data.data,
      timestamp: data.timestamp || Date.now()
    }
  }

  // Handle legacy output format
  if (data.type === 'output') {
    const role = data.stream === 'stdin' ? MessageRole.USER : MessageRole.ASSISTANT
    const content = data.stream === 'stdin'
      ? data.data.replace(/^> /, '')
      : data.data

    return {
      id: data.timestamp || Date.now() + Math.random(),
      role,
      content,
      timestamp: data.timestamp || Date.now()
    }
  }

  return null
}
