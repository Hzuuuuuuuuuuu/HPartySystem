import { useState } from 'react';
import { Alert, Form, Input, Modal, Typography, App as AntdApp } from 'antd';
import { LockOutlined } from '@ant-design/icons';
import { changePassword } from '@/api/auth';

const { Text } = Typography;

interface Props {
  open: boolean;
  /** 解锁前展示的原因，如「您尚未修改过初始密码」 */
  reason?: string;
  /**
   * 是否强制。强制模式下不能点遮罩/ESC 关闭，也不给「取消」按钮 ——
   * 只留「退出登录」一个出口，避免把用户锁死在弹窗里。
   */
  forced?: boolean;
  onSuccess: () => void;
  onCancel?: () => void;
  /** 强制模式下的退出登录回调 */
  onLogout?: () => void;
}

/** 密码强度要求的提示文案，与后端 PasswordPolicy 保持一致 */
const PASSWORD_RULES = [
  { required: true, message: '请输入新密码' },
  { min: 8, max: 32, message: '密码长度须为 8-32 位' },
  {
    pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/,
    message: '密码必须同时包含字母和数字',
  },
];

export default function ChangePasswordModal({
  open,
  reason,
  forced = false,
  onSuccess,
  onCancel,
  onLogout,
}: Props) {
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await changePassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });
      message.success('密码修改成功，请用新密码重新登录');
      form.resetFields();
      onSuccess();
    } catch {
      // 错误提示由 axios 拦截器统一处理（原密码错误、强度不足等都由后端返回中文原因）
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title="修改密码"
      onOk={submit}
      onCancel={forced ? undefined : onCancel}
      confirmLoading={submitting}
      okText="确认修改"
      cancelText={forced ? undefined : '取消'}
      // 强制模式下不允许点遮罩或按 ESC 关闭
      maskClosable={!forced}
      keyboard={!forced}
      closable={!forced}
      footer={
        forced
          ? (_, { OkBtn }) => (
              <>
                <a onClick={onLogout} style={{ marginRight: 12 }}>
                  退出登录
                </a>
                <OkBtn />
              </>
            )
          : undefined
      }
      destroyOnHidden
    >
      {forced && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="需要先修改密码"
          description={
            reason ?? '为了账号安全，请先设置新密码后再使用系统。'
          }
        />
      )}

      <Form form={form} layout="vertical" style={{ marginTop: forced ? 0 : 16 }}>
        <Form.Item
          name="oldPassword"
          label={forced ? '初始密码' : '原密码'}
          rules={[{ required: true, message: '请输入原密码' }]}
        >
          <Input.Password prefix={<LockOutlined />} placeholder="请输入原密码" />
        </Form.Item>

        <Form.Item name="newPassword" label="新密码" rules={PASSWORD_RULES}>
          <Input.Password prefix={<LockOutlined />} placeholder="8-32 位，含字母和数字" />
        </Form.Item>

        <Form.Item
          name="confirmPassword"
          label="确认新密码"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: '请再次输入新密码' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPassword') === value) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error('两次输入的密码不一致'));
              },
            }),
          ]}
        >
          <Input.Password prefix={<LockOutlined />} placeholder="请再次输入新密码" />
        </Form.Item>
      </Form>

      <Text type="secondary" style={{ fontSize: 12 }}>
        密码要求：8-32 位，须同时包含字母和数字，不能包含登录账号，
        不能是连续序列（如 12345678）或常见弱口令。修改后当前登录会失效。
      </Text>
    </Modal>
  );
}
