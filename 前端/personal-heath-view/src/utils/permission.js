import { getToken } from '@/utils/storage'
import request from '@/utils/request'

/**
 * 
 */

/**
 * 
 * @returns {Promise<string[]>} 
 */
export async function getUserPermissions() {
  try {
    const { data } = await request.get('permission/user/0')
    if (data.code === 200) {
      return data.data.map(p => p.code)
    }
  } catch (e) {
    console.error(':', e)
  }
  return []
}

/**
 * 
 * @returns {Promise<string[]>} 
 */
export async function getUserRoles() {
  try {
    const { data } = await request.get('role/user/0')
    if (data.code === 200) {
      return data.data.map(r => r.code)
    }
  } catch (e) {
    console.error(':', e)
  }
  return []
}

/**
 * 
 * @param {string|string[]} permissions - 
 * @param {string[]} userPermissions - 
 * @returns {boolean}
 */
export function hasPermission(permissions, userPermissions = []) {
  if (!permissions) return true
  const permList = Array.isArray(permissions) ? permissions : [permissions]
  return permList.some(p => userPermissions.includes(p))
}

/**
 * 
 * @param {string|string[]} roles - 
 * @param {string[]} userRoles - 
 * @returns {boolean}
 */
export function hasRole(roles, userRoles = []) {
  if (!roles) return true
  const roleList = Array.isArray(roles) ? roles : [roles]
  return roleList.some(r => userRoles.includes(r))
}

/**
 * 
 */
let cachedPermissions = []
let cachedRoles = []

export async function loadUserPermissions() {
  cachedPermissions = await getUserPermissions()
  cachedRoles = await getUserRoles()
  return { permissions: cachedPermissions, roles: cachedRoles }
}

export function getCachedPermissions() {
  return cachedPermissions
}

export function getCachedRoles() {
  return cachedRoles
}
