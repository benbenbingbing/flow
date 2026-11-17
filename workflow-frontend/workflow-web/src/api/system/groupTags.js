import request from '@/utils/request'
const path = id => `/system/group/${encodeURIComponent(id)}`
export const getGroupTags = id => request.get(`${path(id)}/tags`)
export const getGroupMemberTags = id => request.get(`${path(id)}/member-tags`)
export const createGroupTag = (id, tagName) => request.post(`${path(id)}/tags`, { tagName })
export const renameGroupTag = (id, tagId, tagName) => request.post(`${path(id)}/tags/${encodeURIComponent(tagId)}/update`, { tagName })
export const deleteGroupTag = (id, tagId) => request.post(`${path(id)}/tags/${encodeURIComponent(tagId)}/delete`)
export const saveGroupMembersWithTags = (id, data) => request.post(`${path(id)}/members`, data)
export const previewGroupTags = groups => request.post('/system/group/tag-preview', groups)
