import request from './request'

export const recordActionApi = (data) => request.post('/learning/record', data)
export const getStatsApi = () => request.get('/learning/stats')
export const lookupWordApi = (data) => request.post('/learning/word-lookup', data)
export const getVocabularyApi = () => request.get('/learning/vocabulary')
