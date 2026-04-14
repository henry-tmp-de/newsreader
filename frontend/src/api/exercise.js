import request from './request'

export const getExercisesApi = (articleId) => request.get(`/exercises/article/${articleId}`)
export const generateExercisesApi = (articleId) => request.post(`/exercises/generate/${articleId}`, null, {
	timeout: 90000,
})
export const submitAnswerApi = (data) => request.post('/exercises/submit', data)
