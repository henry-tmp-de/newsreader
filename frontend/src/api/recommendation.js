import request from './request'

export const getRecommendationProfileApi = () => request.get('/recommendations/profile')
export const getRecommendedArticlesApi = (params) => request.get('/recommendations/articles', { params })
export const updateRecommendationInterestsApi = (interests) =>
  request.put('/recommendations/interests', { interests })
export const submitRecommendationFeedbackApi = (articleId, feedbackType) =>
  request.post('/recommendations/feedback', { articleId, feedbackType })
