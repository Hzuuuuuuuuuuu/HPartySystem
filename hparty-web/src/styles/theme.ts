import type { ThemeConfig } from 'antd';

/**
 * 中国红主题色板。
 *
 * 取值参考党建系统的视觉惯例与截图配色：
 * 主色为旗帜红，列表选中态用白底红字，内容区留浅灰底以衬托白色卡片。
 */
export const colors = {
  /** 主色：旗帜红 */
  primary: '#C7000B',
  primaryHover: '#A80009',
  primaryActive: '#8F0007',
  /** 渐变用的稍亮红（顶栏、侧边栏顶部） */
  primaryLight: '#DA1A1A',
  /** 深红（侧边栏底部） */
  primaryDark: '#A80009',
  /** 选中项白底上的红字 */
  selectedText: '#C7000B',
  /** 页面底色 */
  pageBg: '#F5F5F5',
  /** 卡片描边 */
  border: '#F0F0F0',
  /** 正文主色 */
  text: '#262626',
  /** 次要文字 */
  textSecondary: '#8C8C8C',
  /** 成功 */
  success: '#52C41A',
  /** 警告 */
  warning: '#FAAD14',
  /** 危险 */
  error: '#FF4D4F',
} as const;

/** Ant Design 主题配置 */
export const antdTheme: ThemeConfig = {
  token: {
    colorPrimary: colors.primary,
    colorLink: colors.primary,
    colorError: colors.error,
    colorSuccess: colors.success,
    colorWarning: colors.warning,
    colorText: colors.text,
    colorTextSecondary: colors.textSecondary,
    borderRadius: 8,
    fontSize: 14,
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif',
    controlHeight: 36,
  },
  components: {
    Layout: {
      bodyBg: colors.pageBg,
      headerBg: colors.primary,
      siderBg: colors.primary,
      headerHeight: 64,
      headerPadding: '0 24px',
    },
    Menu: {
      // 侧边栏为红底，菜单项 hover / 选中态为白底红字
      darkItemBg: 'transparent',
      darkSubMenuItemBg: 'transparent',
      darkItemColor: 'rgba(255, 255, 255, 0.9)',
      darkItemHoverBg: 'rgba(255, 255, 255, 0.16)',
      darkItemHoverColor: '#FFFFFF',
      darkItemSelectedBg: '#FFFFFF',
      darkItemSelectedColor: colors.selectedText,
      itemHeight: 46,
      itemMarginInline: 10,
      itemBorderRadius: 6,
      iconSize: 16,
    },
    Card: {
      borderRadiusLG: 12,
      paddingLG: 20,
    },
    Button: {
      borderRadius: 6,
      controlHeight: 36,
    },
    Table: {
      headerBg: '#FAFAFA',
      headerColor: colors.text,
      borderColor: colors.border,
    },
    Tabs: {
      itemSelectedColor: colors.primary,
      inkBarColor: colors.primary,
    },
  },
};
