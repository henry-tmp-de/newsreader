import request from './request'

export const getApiKeyStatusApi = () => request.get('/system/config/api-keys')
export const saveApiKeysApi = (data) => request.put('/system/config/api-keys', data)
