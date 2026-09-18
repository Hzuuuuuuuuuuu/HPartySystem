import type { ComponentType } from 'react';
import {
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BankOutlined,
  BarChartOutlined,
  BellOutlined,
  BookOutlined,
  CalendarOutlined,
  ClusterOutlined,
  CommentOutlined,
  EditOutlined,
  FileDoneOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  FileWordOutlined,
  FlagOutlined,
  HeartOutlined,
  IdcardOutlined,
  LineChartOutlined,
  LoginOutlined,
  MenuOutlined,
  MoneyCollectOutlined,
  NotificationOutlined,
  PartitionOutlined,
  QuestionCircleOutlined,
  ReadOutlined,
  RiseOutlined,
  SafetyOutlined,
  SettingOutlined,
  SolutionOutlined,
  StarFilled,
  SwapOutlined,
  TeamOutlined,
  TrophyOutlined,
  UserAddOutlined,
  UsergroupAddOutlined,
  UserOutlined,
} from '@ant-design/icons';

/**
 * 图标注册表。
 *
 * <p><b>为什么需要这个文件</b>：菜单表与字典表里的图标是以「组件名」存的
 * （后端下发字符串如 `TeamOutlined`），前端需要按名取组件。最直接的写法是
 * {@code import * as AntIcons from '@ant-design/icons'} 然后动态取值 ——
 * 但那样会把整个图标库（500+ 个图标）全部打进产物，实测 antd chunk 因此膨胀到 1.9MB。</p>
 *
 * <p>改为显式登记本项目实际用到的图标：新增菜单图标时**在这里补一行**，
 * 否则该图标不会被渲染（控制台不会报错，只是图标缺失，容易漏）。</p>
 *
 * <p>图标名的权威来源是：
 * <ul>
 *   <li>{@code sql/02-init-system.sql} 里 {@code sys_menu.icon} 的取值</li>
 *   <li>{@code hparty-common} 的 {@code MaterialCategory} 枚举里的 icon 字段</li>
 * </ul>
 * 改这两处时记得同步本文件。</p>
 */
export const ICON_REGISTRY: Record<string, ComponentType> = {
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BankOutlined,
  BarChartOutlined,
  BellOutlined,
  BookOutlined,
  CalendarOutlined,
  ClusterOutlined,
  CommentOutlined,
  EditOutlined,
  FileDoneOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  FileWordOutlined,
  FlagOutlined,
  HeartOutlined,
  IdcardOutlined,
  LineChartOutlined,
  LoginOutlined,
  MenuOutlined,
  MoneyCollectOutlined,
  NotificationOutlined,
  PartitionOutlined,
  QuestionCircleOutlined,
  ReadOutlined,
  RiseOutlined,
  SafetyOutlined,
  SettingOutlined,
  SolutionOutlined,
  StarFilled,
  SwapOutlined,
  TeamOutlined,
  TrophyOutlined,
  UserAddOutlined,
  UserOutlined,
  UsergroupAddOutlined,
};

/** 按名称取图标组件，未登记时返回 undefined（调用方需自行兜底） */
export function resolveIcon(name?: string): ComponentType | undefined {
  if (!name) return undefined;
  const icon = ICON_REGISTRY[name];
  if (!icon && import.meta.env.DEV) {
    console.warn(`[icon] 图标「${name}」未在 IconRegistry 中登记，将不会渲染`);
  }
  return icon;
}
