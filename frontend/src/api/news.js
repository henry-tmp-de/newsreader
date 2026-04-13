import request from './request'

export const getArticlesApi = (params) => request.get('/articles', { params })
export const getArticleDetailApi = (id) => request.get(`/articles/${id}`)
export const fetchNewsApi = () => request.post('/articles/fetch')
