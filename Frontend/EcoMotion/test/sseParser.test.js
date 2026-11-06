import test from 'node:test'
import assert from 'node:assert/strict'
import { createSseParser } from '../src/utils/sseParser.js'

function parseChunks(chunks) {
  const events = []
  const parser = createSseParser((event) => events.push(event))
  for (const chunk of chunks) parser.push(chunk)
  return { events, parser }
}

test('parses a single named event with JSON data', () => {
  const { events } = parseChunks(['event: BOOKING_CHANGED\ndata: {"bookingCode":"BK-1"}\n\n'])
  assert.deepEqual(events, [{ type: 'BOOKING_CHANGED', data: '{"bookingCode":"BK-1"}', id: null }])
})

test('an event without a name uses the default "message" type', () => {
  const { events } = parseChunks(['data: hello\n\n'])
  assert.deepEqual(events, [{ type: 'message', data: 'hello', id: null }])
})

test('several events in one chunk are all dispatched in order', () => {
  const { events } = parseChunks(['event: A\ndata: 1\n\nevent: B\ndata: 2\n\n'])
  assert.deepEqual(events.map((e) => [e.type, e.data]), [['A', '1'], ['B', '2']])
})

test('an event split across chunks at every byte boundary is reassembled', () => {
  const raw = 'event: DRIVER_ASSIGNED\ndata: {"id":7}\n\nevent: TRIP_STARTED\ndata: {}\n\n'
  for (let cut = 1; cut < raw.length; cut += 1) {
    const { events } = parseChunks([raw.slice(0, cut), raw.slice(cut)])
    assert.deepEqual(events.map((e) => e.type), ['DRIVER_ASSIGNED', 'TRIP_STARTED'], `cut at ${cut}`)
    assert.equal(events[0].data, '{"id":7}', `cut at ${cut}`)
  }
})

test('byte-by-byte delivery produces the same events', () => {
  const raw = 'event: X\ndata: a\ndata: b\n\n'
  const { events } = parseChunks(raw.split(''))
  assert.deepEqual(events, [{ type: 'X', data: 'a\nb', id: null }])
})

test('multiline data is joined with newlines', () => {
  const { events } = parseChunks(['data: line one\ndata: line two\ndata: line three\n\n'])
  assert.equal(events[0].data, 'line one\nline two\nline three')
})

test('CRLF, LF and CR line endings are all accepted, including CRLF split across chunks', () => {
  // "\r" cuối chunk có thể là nửa đầu của CRLF nên phải đợi chunk tiếp theo mới kết thúc dòng.
  const { events } = parseChunks(['event: A\r\ndata: 1\r', '\n\r\nevent: B\rdata: 2\r\r', 'x'])
  assert.deepEqual(events.map((e) => [e.type, e.data]), [['A', '1'], ['B', '2']])
})

test('id is attached to events and persists until changed', () => {
  const { events, parser } = parseChunks(['id: 41\ndata: a\n\ndata: b\n\nid: 42\ndata: c\n\n'])
  assert.deepEqual(events.map((e) => e.id), ['41', '41', '42'])
  assert.equal(parser.lastEventId, '42')
})

test('id containing NUL is ignored', () => {
  const { events } = parseChunks(['id: bad\u0000id\ndata: x\n\n'])
  assert.equal(events[0].id, null)
})

test('comment lines (heartbeats) are ignored and do not dispatch', () => {
  const { events } = parseChunks([': keep-alive\n\n', ':ping\ndata: real\n\n'])
  assert.deepEqual(events, [{ type: 'message', data: 'real', id: null }])
})

test('retry field is parsed as milliseconds and invalid values are ignored', () => {
  const { parser } = parseChunks(['retry: 4000\ndata: x\n\n'])
  assert.equal(parser.retryMs, 4000)
  const { parser: bad } = parseChunks(['retry: soon\ndata: x\n\n'])
  assert.equal(bad.retryMs, null)
})

test('an incomplete event at end of stream is not dispatched', () => {
  const { events } = parseChunks(['event: A\ndata: partial\n'])
  assert.deepEqual(events, [])
})

test('a blank line without data dispatches nothing', () => {
  const { events } = parseChunks(['event: ignored\n\n\n\ndata: ok\n\n'])
  assert.deepEqual(events, [{ type: 'message', data: 'ok', id: null }])
})

test('a leading BOM is stripped', () => {
  const { events } = parseChunks(['﻿event: A\ndata: 1\n\n'])
  assert.deepEqual(events, [{ type: 'A', data: '1', id: null }])
})

test('field without colon is treated as a field with empty value', () => {
  const { events } = parseChunks(['data\n\n'])
  assert.deepEqual(events, [{ type: 'message', data: '', id: null }])
})
