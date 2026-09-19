export function errorMessage(error: any, fallback = '操作失败，请稍后重试') {
  return error?.response?.data?.message || error?.message || fallback
}
