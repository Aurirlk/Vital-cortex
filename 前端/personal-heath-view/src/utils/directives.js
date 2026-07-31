import { hasPermission, hasRole, getCachedPermissions, getCachedRoles } from './permission'

/**
 * v-permission 
 * v-permission="'user:create'"  v-permission="['user:create', 'user:update']"
 */
export const permissionDirective = {
  mounted(el, binding) {
    const { value } = binding
    if (!value) return

    const userPermissions = getCachedPermissions()
    if (!hasPermission(value, userPermissions)) {
      el.parentNode?.removeChild(el)
    }
  }
}

/**
 * v-role 
 * v-role="'admin'"  v-role="['admin', 'doctor']"
 */
export const roleDirective = {
  mounted(el, binding) {
    const { value } = binding
    if (!value) return

    const userRoles = getCachedRoles()
    if (!hasRole(value, userRoles)) {
      el.parentNode?.removeChild(el)
    }
  }
}

/**
 * 
 */
export function setupPermissionDirectives(app) {
  app.directive('permission', permissionDirective)
  app.directive('role', roleDirective)
}
