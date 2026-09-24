import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Button, Form, Input, Select, App as AntdApp } from 'antd';
import { LockOutlined, SafetyOutlined, UserOutlined, PhoneOutlined, IdcardOutlined } from '@ant-design/icons';
import logo from '@/assets/logo.png';
import { getCaptcha, register, type CaptchaVO } from '@/api/auth';

interface RegisterForm {
  username: string;
  password: string;
  confirmPassword: string;
  name: string;
  gender?: number;
  idCard?: string;
  phone: string;
  code: string;
}

export default function RegisterPage() {
  const navigate = useNavigate();
  const { message } = AntdApp.useApp();

  const [form] = Form.useForm<RegisterForm>();
  const [loading, setLoading] = useState(false);
  const [captcha, setCaptcha] = useState<CaptchaVO>({ uuid: null, img: null, enabled: false });

  const refreshCaptcha = async () => {
    try {
      const res = await getCaptcha();
      setCaptcha(res);
    } catch {
      // 验证码接口异常不阻断注册页渲染
    }
  };

  useEffect(() => {
    refreshCaptcha();
  }, []);

  const onFinish = async (values: RegisterForm) => {
    setLoading(true);
    try {
      await register({
        username: values.username.trim(),
        password: values.password,
        confirmPassword: values.confirmPassword,
        name: values.name.trim(),
        gender: values.gender,
        idCard: values.idCard?.trim(),
        phone: values.phone.trim(),
        code: values.code,
        uuid: captcha.uuid ?? '',
      });

      message.success('注册成功，请登录');
      navigate('/login', { replace: true });
    } catch {
      // 错误提示已由 axios 拦截器统一处理，这里只需刷新验证码
      form.setFieldValue('code', '');
      refreshCaptcha();
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card" style={{ maxWidth: 480 }}>
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

        <div className="login-card__title">用户注册</div>
        <div className="login-card__subtitle">入党申请人自助注册</div>

        <Form form={form} size="large" onFinish={onFinish} autoComplete="off" layout="vertical">
          <Form.Item
            name="username"
            label="登录账号"
            rules={[
              { required: true, message: '请输入登录账号' },
              { min: 4, max: 20, message: '账号长度为4-20个字符' },
              { pattern: /^[a-zA-Z0-9_]+$/, message: '只能包含字母、数字、下划线' },
            ]}
          >
            <Input prefix={<UserOutlined />} placeholder="4-20位字母数字下划线" allowClear />
          </Form.Item>

          <Form.Item
            name="password"
            label="密码"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 8, max: 20, message: '密码长度为8-20个字符' },
              {
                pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/,
                message: '密码必须包含大小写字母和数字',
              },
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="8-20位，含大小写字母和数字" />
          </Form.Item>

          <Form.Item
            name="confirmPassword"
            label="确认密码"
            dependencies={['password']}
            rules={[
              { required: true, message: '请再次输入密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="请再次输入密码" />
          </Form.Item>

          <Form.Item
            name="name"
            label="真实姓名"
            rules={[
              { required: true, message: '请输入真实姓名' },
              { min: 2, max: 50, message: '姓名长度为2-50个字符' },
            ]}
          >
            <Input prefix={<UserOutlined />} placeholder="请输入真实姓名" allowClear />
          </Form.Item>

          <Form.Item name="gender" label="性别">
            <Select placeholder="请选择性别" allowClear>
              <Select.Option value={1}>男</Select.Option>
              <Select.Option value={0}>女</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="idCard"
            label="身份证号"
            rules={[
              {
                pattern: /^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]$/,
                message: '身份证号格式不正确',
              },
            ]}
          >
            <Input
              prefix={<IdcardOutlined />}
              placeholder="选填，18位身份证号"
              maxLength={18}
              allowClear
            />
          </Form.Item>

          <Form.Item
            name="phone"
            label="手机号"
            rules={[
              { required: true, message: '请输入手机号' },
              { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' },
            ]}
          >
            <Input
              prefix={<PhoneOutlined />}
              placeholder="11位手机号"
              maxLength={11}
              allowClear
            />
          </Form.Item>

          {captcha.enabled && (
            <Form.Item name="code" label="验证码" rules={[{ required: true, message: '请输入验证码' }]}>
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
              注 册
            </Button>
          </Form.Item>

          <Form.Item style={{ marginBottom: 0 }}>
            <div style={{ textAlign: 'center' }}>
              已有账号？
              <Link to="/login" style={{ marginLeft: 8 }}>
                立即登录
              </Link>
            </div>
          </Form.Item>
        </Form>

        <div className="login-card__tips">
          <div style={{ fontSize: 13, color: '#666' }}>
            注册须知：
          </div>
          <div style={{ fontSize: 12 }}>
            • 注册后自动创建入党申请人档案
          </div>
          <div style={{ fontSize: 12 }}>
            • 请使用真实姓名和联系方式
          </div>
          <div style={{ fontSize: 12 }}>
            • 密码必须包含大小写字母和数字
          </div>
        </div>
      </div>
    </div>
  );
}
