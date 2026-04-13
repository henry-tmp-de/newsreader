import request from './request'

export const getArticlesApi = (params) => request.get('/articles', { params })
export const getArticleDetailApi = (id) => request.get(`/articles/${id}`)
export const fetchNewsApi = () => request.post('/articles/fetch')
export const fetchNewsCustomApi = (data) => request.post('/articles/fetch/custom', data)
export const deleteArticleApi = (id) => request.delete(`/articles/${id}`)
export const deleteArticlesBatchApi = (ids) => request.post('/articles/batch-delete', { ids })
export const clearArticlesApi = () => request.post('/articles/clear-all')
