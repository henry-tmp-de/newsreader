import request from './request'

export const loginApi = (data) => request.post('/user/login', data)
export const registerApi = (data) => request.post('/user/register', data)
export const getProfileApi = () => request.get('/user/profile')
export const updateProfileApi = (data) => request.put('/user/profile', data)
