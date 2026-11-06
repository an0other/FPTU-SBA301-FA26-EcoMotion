// Bộ parse text/event-stream theo đặc tả HTML (WHATWG), dùng cho fetch streaming.
// Xử lý: chunk bị cắt giữa dòng, nhiều event trong một chunk, dữ liệu nhiều dòng, id, retry,
// dòng comment (heartbeat ":..."), kết thúc dòng CRLF/LF/CR. Event chưa có dòng trống kết thúc
// bị bỏ qua, đúng đặc tả.

export function createSseParser(onEvent) {
  let buffer = ''
  let atStart = true
  let dataLines = []
  let eventName = ''
  let lastEventId = null
  let retryMs = null

  function dispatch() {
    if (dataLines.length === 0) {
      eventName = ''
      return
    }
    const type = eventName || 'message'
    const data = dataLines.join('\n')
    dataLines = []
    eventName = ''
    onEvent({ type, data, id: lastEventId })
  }

  function processLine(line) {
    if (line === '') {
      dispatch()
      return
    }
    if (line.startsWith(':')) return // comment / heartbeat

    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)

    if (field === 'event') eventName = value
    else if (field === 'data') dataLines.push(value)
    else if (field === 'id') {
      if (!value.includes('\0')) lastEventId = value
    } else if (field === 'retry') {
      if (/^\d+$/.test(value)) retryMs = Number(value)
    }
  }

  return {
    push(chunk) {
      buffer += chunk
      if (atStart && buffer.length > 0) {
        if (buffer.charCodeAt(0) === 0xfeff) buffer = buffer.slice(1)
        atStart = false
      }
      let start = 0
      while (start < buffer.length) {
        let end = -1
        for (let i = start; i < buffer.length; i += 1) {
          const ch = buffer[i]
          if (ch === '\n' || ch === '\r') {
            end = i
            break
          }
        }
        if (end === -1) break
        // "\r" ở cuối buffer có thể là nửa đầu của CRLF: đợi chunk tiếp theo.
        if (buffer[end] === '\r' && end === buffer.length - 1) break
        processLine(buffer.slice(start, end))
        start = buffer[end] === '\r' && buffer[end + 1] === '\n' ? end + 2 : end + 1
      }
      buffer = buffer.slice(start)
    },
    get lastEventId() {
      return lastEventId
    },
    get retryMs() {
      return retryMs
    },
  }
}
