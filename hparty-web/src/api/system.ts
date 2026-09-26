import { http } from './request';
import type { PageQuery, PageResult } from '@/types/api';

// ==================== 人员档案 ====================

export interface PartyPerson {
  personId: number;
  personNo?: string;
  name: string;
  sex?: number;
  /** 性别中文，后端 PartyPersonVO 下发 */
  sexLabel?: string;
  idCard?: string;
  birthDate?: string;
  age?: number;
  nation?: string;
  /** 籍贯 */
  nativePlace?: string;
  phone?: string;
  education?: string;
  workUnit?: string;
  jobTitle?: string;
  orgId?: number;
  orgName?: string;
  groupId?: number;
  avatar?: string;
  memberStatus?: number;
  memberStatusLabel?: string;
  politicalStatus?: string;
  isMember?: number;
  applyDate?: string;
  activistDate?: string;
  candidateDate?: string;
  probationaryDate?: string;
  fullMemberDate?: string;
  partyAge?: number;
}

export interface PartyPersonQuery extends PageQuery {
  name?: string;
  phone?: string;
  idCard?: string;
  orgId?: number;
  memberStatus?: number;
  politicalStatus?: string;
}

/** 下拉选择用的精简结构 */
export interface PersonOption {
  personId: number;
  name: string;
  orgName?: string;
}

/** 人员分页 */
export const pagePersons = (params: PartyPersonQuery) =>
  http.get<PageResult<PartyPerson>>('/system/person/page', params);

/** 党员分页 */
export const pageMembers = (params: PartyPersonQuery) =>
  http.get<PageResult<PartyPerson>>('/system/person/members', params);

/** 人员详情 */
export const getPerson = (personId: number) =>
  http.get<PartyPerson>(`/system/person/${personId}`);

/** 按组织查人员 */
export const listPersonByOrg = (orgId: number) =>
  http.get<PartyPerson[]>(`/system/person/org/${orgId}`);

/** 人员统计 */
export const getPersonStatistics = () =>
  http.get<Record<string, unknown>>('/system/person/statistics');

/** 新增人员 */
export const addPerson = (data: Partial<PartyPerson>) =>
  http.post<number>('/system/person', data);

/** 修改人员 */
export const updatePerson = (data: Partial<PartyPerson>) =>
  http.put<void>('/system/person', data);

/** 删除人员 */
export const removePerson = (personId: number) =>
  http.delete<void>(`/system/person/${personId}`);

/**
 * 人员下拉选项。
 * 取前 500 条供选择器使用（演示规模足够；真实大单位应改为远程搜索）。
 */
export const listPersonOptions = async (keyword?: string): Promise<PersonOption[]> => {
  const res = await pagePersons({ pageNum: 1, pageSize: 500, name: keyword });
  return (res.records ?? []).map((p) => ({
    personId: p.personId,
    name: p.name,
    orgName: p.orgName,
  }));
};

// ==================== 党组织 ====================

export interface SysDept {
  orgId: number;
  parentId: number;
  orgName: string;
  orgShortName?: string;
  orgCode?: string;
  orgType: number;
  orgTypeLabel?: string;
  orgLevel?: number;
  leader?: string;
  phone?: string;
  address?: string;
  secretaryId?: number;
  secretaryName?: string;
  memberCount?: number;
  foundedDate?: string;
  orgPath?: string;
  orderNum?: number;
  status?: number;
  children?: SysDept[];
}

/** 党组织树（用于图4 组织架构图） */
export const listDeptTree = () => http.get<SysDept[]>('/system/dept/tree');

/**
 * 党组织列表。
 * 后端 /system/dept/list 返回分页结构，这里统一转成数组（兼容直接返回数组的实现）。
 */
export const listDepts = async (params?: Record<string, unknown>): Promise<SysDept[]> => {
  const res = await http.get<PageResult<SysDept> | SysDept[]>('/system/dept/list', {
    pageNum: 1,
    pageSize: 500,
    ...params,
  });
  if (Array.isArray(res)) return res;
  return res?.records ?? [];
};

export const getDept = (orgId: number) => http.get<SysDept>(`/system/dept/${orgId}`);

export const addDept = (data: Partial<SysDept>) => http.post<number>('/system/dept', data);

export const updateDept = (data: Partial<SysDept>) => http.put<void>('/system/dept', data);

export const removeDept = (orgId: number) => http.delete<void>(`/system/dept/${orgId}`);

// ==================== 字典 ====================

export interface DictData {
  dictCode: number;
  dictLabel: string;
  dictValue: string;
  dictType: string;
  dictSort?: number;
  listClass?: string;
  cssClass?: string;
  isDefault?: number;
  status?: number;
  remark?: string;
}

/** 按类型取字典项 */
export const listDictByType = (dictType: string) =>
  http.get<DictData[]>(`/system/dict/data/type/${dictType}`);

// ==================== 三会一课 ====================

export interface AmMeeting {
  meetingId: number;
  meetingType: string;
  meetingTypeLabel?: string;
  title: string;
  orgId: number;
  orgName?: string;
  content?: string;
  meetingDate?: string;
  startTime?: string;
  endTime?: string;
  place?: string;
  hostName?: string;
  recorderName?: string;
  shouldAttend?: number;
  actualAttend?: number;
  status?: number;
}

export interface AmMaterial {
  materialId: number;
  meetingId?: number;
  orgId: number;
  category: string;
  categoryLabel?: string;
  title: string;
  content?: string;
  fileUrl?: string;
  uploadName?: string;
  createTime?: string;
}

// ==================== 用户管理 ====================

export interface SysUser {
  userId: number;
  username: string;
  nickName?: string;
  personId?: number;
  orgId?: number;
  orgName?: string;
  phone?: string;
  email?: string;
  avatar?: string;
  sex?: number;
  status?: number;
  loginIp?: string;
  loginDate?: string;
  remark?: string;
  /** 已分配的角色 id 集合 */
  roleIds?: number[];
  /** 角色名称集合（后端为 List<String>，兼容字符串形式的实现） */
  roleNames?: string[] | string;
  createTime?: string;
}

export interface SysUserQuery extends PageQuery {
  username?: string;
  nickName?: string;
  phone?: string;
  orgId?: number;
  status?: number;
}

/** 用户分页 */
export const pageUsers = (params: SysUserQuery) =>
  http.get<PageResult<SysUser>>('/system/user/list', params);

/** 用户详情 */
export const getUser = (userId: number) => http.get<SysUser>(`/system/user/${userId}`);

/** 新增用户 */
export const addUser = (data: Record<string, unknown>) => http.post<number>('/system/user', data);

/** 修改用户（不含密码） */
export const updateUser = (data: Record<string, unknown>) => http.put<void>('/system/user', data);

/** 删除用户 */
export const removeUser = (userId: number) => http.delete<void>(`/system/user/${userId}`);

/** 重置密码 */
export const resetUserPassword = (userId: number, password: string) =>
  http.put<void>(`/system/user/${userId}/password`, { password });

/** 启用/停用：0=停用 1=正常。status 走 query 参数（后端 @RequestParam） */
export const changeUserStatus = (userId: number, status: number) =>
  http.put<void>(`/system/user/${userId}/status`, null, { params: { status } });

// ==================== 角色管理 ====================

export interface SysRole {
  roleId: number;
  roleName: string;
  roleKey: string;
  roleSort?: number;
  /** 1=全部 2=本级 3=本级及以下 4=仅本人 5=自定义 */
  dataScope?: number;
  dataScopeLabel?: string;
  isBuiltin?: number;
  status?: number;
  remark?: string;
  /** 已勾选的菜单 id 集合 */
  menuIds?: number[];
  /** 自定义数据范围时选中的组织 id 集合 */
  deptIds?: number[];
  createTime?: string;
}

export interface SysRoleQuery extends PageQuery {
  roleName?: string;
  roleKey?: string;
  status?: number;
}

/** 角色分页 */
export const pageRoles = (params: SysRoleQuery) =>
  http.get<PageResult<SysRole>>('/system/role/list', params);

/**
 * 角色下拉数据。
 * 后端 /system/role/list 在分页场景返回 PageResult，未分页时可能直接返回数组，
 * 这里做兼容处理，统一返回数组。
 */
export const listRoleOptions = async (): Promise<SysRole[]> => {
  const res = await http.get<PageResult<SysRole> | SysRole[]>('/system/role/list', {
    pageNum: 1,
    pageSize: 500,
  });
  if (Array.isArray(res)) return res;
  return res?.records ?? [];
};

/** 角色详情（含 menuIds） */
export const getRole = (roleId: number) => http.get<SysRole>(`/system/role/${roleId}`);

export const addRole = (data: Record<string, unknown>) => http.post<number>('/system/role', data);

export const updateRole = (data: Record<string, unknown>) => http.put<void>('/system/role', data);

export const removeRole = (roleId: number) => http.delete<void>(`/system/role/${roleId}`);

/** 分配菜单权限，请求体为纯数组 [1,2,3] */
export const assignRoleMenus = (roleId: number, menuIds: number[]) =>
  http.put<void>(`/system/role/${roleId}/menus`, menuIds);

/** 启用/停用角色。status 走 query 参数（后端 @RequestParam） */
export const changeRoleStatus = (roleId: number, status: number) =>
  http.put<void>(`/system/role/${roleId}/status`, null, { params: { status } });

// ==================== 菜单管理 ====================

export interface SysMenu {
  menuId: number;
  parentId: number;
  menuName: string;
  orderNum?: number;
  path?: string;
  component?: string;
  query?: string;
  isFrame?: number;
  isCache?: number;
  /** M=目录 C=菜单 F=按钮 */
  menuType: 'M' | 'C' | 'F';
  visible?: number;
  status?: number;
  perms?: string;
  icon?: string;
  remark?: string;
  createTime?: string;
  children?: SysMenu[];
}

/**
 * 菜单列表（平铺，含所有层级）。
 * 后端 /system/menu/list 返回分页结构，这里统一转成数组，单页上限 500 足够放下全部菜单。
 */
export const listMenus = async (params?: Record<string, unknown>): Promise<SysMenu[]> => {
  const res = await http.get<PageResult<SysMenu> | SysMenu[]>('/system/menu/list', {
    pageNum: 1,
    pageSize: 500,
    ...params,
  });
  if (Array.isArray(res)) return res;
  return res?.records ?? [];
};

/** 菜单树 */
export const listMenuTree = () => http.get<SysMenu[]>('/system/menu/tree');

export const getMenu = (menuId: number) => http.get<SysMenu>(`/system/menu/${menuId}`);

export const addMenu = (data: Record<string, unknown>) => http.post<number>('/system/menu', data);

export const updateMenu = (data: Record<string, unknown>) => http.put<void>('/system/menu', data);

export const removeMenu = (menuId: number) => http.delete<void>(`/system/menu/${menuId}`);

// ==================== 字典管理 ====================

export interface DictType {
  dictId: number;
  dictName: string;
  dictType: string;
  status?: number;
  remark?: string;
  createTime?: string;
}

export interface DictTypeQuery extends PageQuery {
  dictName?: string;
  dictType?: string;
  status?: number;
}

export interface DictDataQuery extends PageQuery {
  dictType?: string;
  dictLabel?: string;
  status?: number;
}

/** 字典类型分页 */
export const pageDictTypes = (params: DictTypeQuery) =>
  http.get<PageResult<DictType>>('/system/dict/type/page', params);

export const getDictType = (dictId: number) => http.get<DictType>(`/system/dict/type/${dictId}`);

export const addDictType = (data: Record<string, unknown>) =>
  http.post<number>('/system/dict/type', data);

export const updateDictType = (data: Record<string, unknown>) =>
  http.put<void>('/system/dict/type', data);

export const removeDictType = (dictId: number) =>
  http.delete<void>(`/system/dict/type/${dictId}`);

/** 字典数据分页 */
export const pageDictData = (params: DictDataQuery) =>
  http.get<PageResult<DictData>>('/system/dict/data/page', params);

export const getDictData = (dictCode: number) =>
  http.get<DictData>(`/system/dict/data/${dictCode}`);

export const addDictData = (data: Record<string, unknown>) =>
  http.post<number>('/system/dict/data', data);

export const updateDictData = (data: Record<string, unknown>) =>
  http.put<void>('/system/dict/data', data);

export const removeDictData = (dictCode: number) =>
  http.delete<void>(`/system/dict/data/${dictCode}`);

/** 刷新字典缓存 */
export const refreshDictCache = () => http.delete<void>('/system/dict/cache');

// ==================== 日志 ====================

export interface LoginLog {
  infoId: number;
  username?: string;
  ipaddr?: string;
  loginLocation?: string;
  browser?: string;
  os?: string;
  /** 0=失败 1=成功 */
  status?: number;
  msg?: string;
  loginTime?: string;
}

export interface LoginLogQuery extends PageQuery {
  username?: string;
  status?: number;
  beginTime?: string;
  endTime?: string;
}

export interface OperLog {
  operId: number;
  title?: string;
  /** 0=其它 1=新增 2=修改 3=删除 4=审批 5=导出 6=上传 7=导入 8=授权 */
  businessType?: number;
  method?: string;
  requestMethod?: string;
  operName?: string;
  operUrl?: string;
  operIp?: string;
  operParam?: string;
  jsonResult?: string;
  status?: number;
  errorMsg?: string;
  costTime?: number;
  operTime?: string;
}

export interface OperLogQuery extends PageQuery {
  title?: string;
  username?: string;
  businessType?: number;
  status?: number;
  beginTime?: string;
  endTime?: string;
}

/** 登录日志分页 */
export const pageLoginLogs = (params: LoginLogQuery) =>
  http.get<PageResult<LoginLog>>('/system/log/login/page', params);

/** 删除登录日志，ids 为逗号分隔的多个 id */
export const removeLoginLogs = (ids: string) =>
  http.delete<void>(`/system/log/login/${ids}`);

/** 清空登录日志 */
export const cleanLoginLogs = () => http.delete<void>('/system/log/login/clean');

/** 操作日志分页 */
export const pageOperLogs = (params: OperLogQuery) =>
  http.get<PageResult<OperLog>>('/system/log/oper/page', params);

/** 操作日志详情 */
export const getOperLog = (operId: number) => http.get<OperLog>(`/system/log/oper/${operId}`);

/** 删除操作日志，ids 为逗号分隔的多个 id */
export const removeOperLogs = (ids: string) => http.delete<void>(`/system/log/oper/${ids}`);

/** 清空操作日志 */
export const cleanOperLogs = () => http.delete<void>('/system/log/oper/clean');
