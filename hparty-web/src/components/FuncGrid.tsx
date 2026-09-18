import { Card } from 'antd';
import { resolveIcon } from '@/components/IconRegistry';

export interface FuncItem {
  key: string;
  label: string;
  /** Ant Design 图标组件名，如 UsergroupAddOutlined */
  icon: string;
  onClick?: () => void;
}

interface Props {
  items: FuncItem[];
  /** 卡片标题，不传则不显示标题栏 */
  title?: string;
}

/**
 * 功能入口九宫格（对应图1 三会一课、图2 组织生活会的圆形大图标入口）。
 */
export default function FuncGrid({ items, title }: Props) {
  return (
    <Card variant="borderless" title={title} styles={{ body: { padding: 12 } }}>
      <div className="func-grid">
        {items.map((item) => {
          const Icon = resolveIcon(item.icon);
          return (
            <div className="func-item" key={item.key} onClick={item.onClick}>
              <div className="func-item__circle">{Icon ? <Icon /> : null}</div>
              <div className="func-item__label">{item.label}</div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}
