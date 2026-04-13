import request from './request'

export const recordActionApi = (data) => request.post('/learning/record', data)
export const getStatsApi = () => request.get('/learning/stats')
export const lookupWordApi = (data) => request.post('/learning/word-lookup', data)
export const lookupTextApi = (data) => request.post('/learning/text-lookup', data)
export const articleChatApi = (data) => request.post('/learning/article-chat', data)
export const getVocabularyApi = () => request.get('/learning/vocabulary')
