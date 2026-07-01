export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
  traceId: string
}
