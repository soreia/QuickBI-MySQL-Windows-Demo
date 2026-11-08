let token = ''
export function csrf(value: string) { token = value }
export class ApiFailure extends Error {
  constructor(public status: number, public code: string, message: string, public requestId: string) { super(message) }
}
export async function api<T = Record<string, any>>(path: string, method = 'GET', body?: unknown, headers: Record<string,string> = {}): Promise<T> {
  const response = await fetch(path, {
    method, credentials: 'same-origin', cache: 'no-store',
    headers: {'Content-Type':'application/json', ...(method === 'GET' ? {} : {'X-CSRF-Token':token}), ...headers},
    ...(body === undefined ? {} : {body: JSON.stringify(body)}),
  })
  if (response.status === 204) return undefined as T
  const data = await response.json()
  if (!response.ok) throw new ApiFailure(response.status, data.error?.code ?? 'HTTP_ERROR', data.error?.message ?? '请求失败', data.requestId ?? '')
  return data.data as T
}
