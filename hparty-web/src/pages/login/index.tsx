import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Form, Input, App as AntdApp } from 'antd';
import { LockOutlined, SafetyOutlined, UserOutlined } from '@ant-design/icons';
import logo from '@/assets/logo.png';
import { getCaptcha, type CaptchaVO } from '@/api/auth';
import { useUserStore } from '@/store/user';
import ChangePasswordModal from '@/components/ChangePasswordModal';

interface LoginForm {
  username: string;
  password: string;
  code?: string;
}

export default function LoginPage() {
  const navigate = useNavigate();
  const { message } = AntdApp.useApp();
  const { login, loadUserInfo } = useUserStore();

  const [form] = Form.useForm<LoginForm>();
  const [loading, setLoading] = useState(false);
  const [captcha, setCaptcha] = useState<CaptchaVO>({ uuid: null, img: null, enabled: false });
  const [showChangePwd, setShowChangePwd] = useState(false);
  const [changePwdReason, setChangePwdReason] = useState('');

  const refreshCaptcha = async () => {
    try {
      const res = await getCaptcha();
      setCaptcha(res);
    } catch {
      // 验证码接口异常不阻断登录页渲染
    }
  };

  useEffect(() => {
    refreshCaptcha();
  }, []);

  const { clearLocal } = useUserStore();

  const onFinish = async (values: LoginForm) => {
    setLoading(true);
    try {
      const res = await login({
        username: values.username.trim(),
        password: values.password,
        code: values.code,
        uuid: captcha.uuid ?? undefined,
      });
      await loadUserInfo();

      // 服务端要求强制改密（从未改过初始密码 / 密码已过期）时，
      // 不进入业务系统，先弹出改密对话框。
      if (res.needChangePwd) {
        setChangePwdReason(res.changePwdReason ?? '');
        setShowChangePwd(true);
        return;
      }

      message.success('登录成功');
      navigate('/dashboard', { replace: true });
    } catch {
      // 错误提示已由 axios 拦截器统一处理，这里只需刷新验证码
      form.setFieldValue('code', '');
      refreshCaptcha();
    } finally {
      setLoading(false);
    }
  };

  /** 改密成功后服务端已把会话作废，须回到登录页用新密码重登 */
  const handlePasswordChanged = () => {
    setShowChangePwd(false);
    clearLocal();
    form.resetFields();
    refreshCaptcha();
    message.info('请使用新密码重新登录');
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <div style={{ textAlign: 'center', marginBottom: 8 }}>
          <div
            style={{
              width: 60,
              height: 60,
              margin: '0 auto 14px',
              borderRadius: '50%',
              background: '#fff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <img src={logo} alt="logo" style={{ width: 52, height: 52, objectFit: 'contain' }} />
          </div>
        </div>

        <div className="login-card__title">智慧党建管理系统</div>
        <div className="login-card__subtitle">智慧党建</div>

        <Form form={form} size="large" onFinish={onFinish} autoComplete="off">
          <Form.Item name="username" rules={[{ required: true, message: '请输入登录账号' }]}>
            <Input prefix={<UserOutlined />} placeholder="登录账号" allowClear />
          </Form.Item>

          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>

          {captcha.enabled && (
            <Form.Item name="code" rules={[{ required: true, message: '请输入验证码' }]}>
              <div className="login-card__captcha">
                <Input
                  prefix={<SafetyOutlined />}
                  placeholder="验证码"
                  maxLength={6}
                  style={{ flex: 1 }}
                />
                <img
                  className="login-card__captcha-img"
                  src={captcha.img ?? undefined}
                  alt="点击刷新验证码"
                  title="点击刷新"
                  onClick={refreshCaptcha}
                />
              </div>
            </Form.Item>
          )}

          <Form.Item style={{ marginBottom: 8 }}>
            <Button type="primary" htmlType="submit" loading={loading} block size="large">
              登 录
            </Button>
          </Form.Item>
        </Form>

        <div className="login-card__tips">
          <div>演示账号（初始密码均为 123456）：</div>
          <div>admin — 超级管理员　|　zsf — 第一支部书记</div>
          <div>liming — 组织委员　|　zw — 第二支部书记</div>
          <div>zgq — 郑州市党委书记（办理上级党委审批类步骤）</div>
        </div>
      </div>

      {/* 强制改密：初始密码未改或密码已过期时，登录后先改密再进系统 */}
      <ChangePasswordModal
        open={showChangePwd}
        reason={changePwdReason}
        forced
        onSuccess={handlePasswordChanged}
        onLogout={() => {
          setShowChangePwd(false);
          clearLocal();
        }}
      />
    </div>
  );
}
