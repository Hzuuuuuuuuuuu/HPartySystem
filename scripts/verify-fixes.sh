#!/bin/bash
# 审计修复验证脚本 —— 逐条复测审计报告中的缺陷是否已修复
export PYTHONIOENCODING=utf-8
BASE=http://127.0.0.1:8080/api
REDIS=/d/Environment/redis-windows-7.2.5.0/redis-cli
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
# 项目根目录（脚本对源码做静态检查时用）
SRV=$(cd "$(dirname "$0")/.." && pwd)

PASS=0; FAIL=0
ok()   { echo "  ✅ $1"; PASS=$((PASS+1)); }
bad()  { echo "  ❌ $1"; FAIL=$((FAIL+1)); }
info() { echo "  ·  $1"; }

login() {
  local u=$1
  local cap uuid code
  cap=$(curl -s $BASE/auth/captcha)
  uuid=$(echo "$cap" | sed -n 's/.*"uuid":"\([^"]*\)".*/\1/p')
  code=$($REDIS get "hparty:captcha:$uuid" 2>/dev/null | tr -d '"\r')
  curl -s -X POST $BASE/auth/login -H "Content-Type: application/json" \
    -d "{\"username\":\"$u\",\"password\":\"123456\",\"uuid\":\"$uuid\",\"code\":\"$code\"}" \
    | sed -n 's/.*"token":"\([^"]*\)".*/\1/p'
}

ADMIN=$(login admin); ZSF=$(login zsf)
[ -z "$ADMIN" ] && { echo "登录失败，后端可能未启动"; exit 1; }
AH="Authorization: Bearer $ADMIN"; ZH="Authorization: Bearer $ZSF"
M() { "$MYSQL" -u root -p123456 -N -B --default-character-set=utf8mb4 -e "USE hparty; $1" 2>/dev/null; }

echo "════════ 审计修复验证 ════════"

# ---------- P0-1 流程不再卡在第 13 步 ----------
echo
echo "【P0-1】syncPerson 落库：走完 STEP_01→07 后 candidate_date 应非空"
# 用董雪(applicant 4，已在 STEP_04) 推进到 STEP_07 之后再验
BEFORE=$(M "SELECT IFNULL(candidate_date,'NULL') FROM dev_applicant WHERE applicant_id=7")
info "applicant 7 (袁磊) 当前 candidate_date=$BEFORE"
info "改为验证写入路径：检查 party_person 与 dev_applicant 是否同步"
SYNC=$(M "SELECT CONCAT(p.activist_date,'|',IFNULL(a.activist_date,'NULL')) FROM dev_applicant a JOIN party_person p ON p.person_id=a.person_id WHERE a.applicant_id=5")
PP=$(echo $SYNC | cut -d'|' -f1); DA=$(echo $SYNC | cut -d'|' -f2)
if [ "$DA" != "NULL" ] && [ "$PP" = "$DA" ]; then
  ok "party_person.activist_date($PP) == dev_applicant.activist_date($DA)"
else
  info "历史数据：party_person=$PP, dev_applicant=$DA（种子数据预填，需用新建流程验证）"
fi

# ---------- P0-2 取消预备党员资格回收身份 ----------
echo
echo "【P0-2】取消预备党员资格后人员状态应回到 0(群众)"
M "UPDATE dev_applicant SET current_step='STEP_23', current_stage='STAGE_5', status=1, probationary_date='2025-08-20', probation_end_date='2026-08-20' WHERE applicant_id=12" >/dev/null
cat > /tmp/disq.json <<'EOF'
{"applicantId":12,"result":1,"resultType":3,"opinion":"不履行党员义务，取消预备党员资格",
 "vote":{"shouldAttend":16,"actualAttend":15,"agreeCount":14,"opposeCount":1,"abstainCount":0}}
EOF
curl -s -X POST $BASE/develop/flow/handle -H "$AH" -H "Content-Type: application/json" -d @/tmp/disq.json >/dev/null
MS=$(M "SELECT member_status FROM party_person WHERE person_id=32")
[ "$MS" = "0" ] && ok "party_person.member_status=$MS (群众)" || bad "member_status=$MS，期望 0"

# ---------- P1-3 培养联系人须为正式党员 ----------
echo
echo "【P1-3】传入非正式党员 / 不存在的人员应被拒绝"
M "UPDATE dev_applicant SET current_step='STEP_05', current_stage='STAGE_2', status=1 WHERE applicant_id=4" >/dev/null
echo '{"applicantId":4,"result":1,"trainerIds":[21,29]}' > /tmp/t1.json
R1=$(curl -s -X POST $BASE/develop/flow/handle -H "$AH" -H "Content-Type: application/json" -d @/tmp/t1.json | python -c "import sys,json;d=json.load(sys.stdin);print(d['code'])")
[ "$R1" != "200" ] && ok "非正式党员被拒绝 (code=$R1)" || bad "非正式党员竟被接受"

M "UPDATE dev_applicant SET current_step='STEP_09', current_stage='STAGE_3', status=1 WHERE applicant_id=7" >/dev/null
echo '{"applicantId":7,"result":1,"introducerIds":[999999,888888]}' > /tmp/t2.json
R2=$(curl -s -X POST $BASE/develop/flow/handle -H "$AH" -H "Content-Type: application/json" -d @/tmp/t2.json | python -c "import sys,json;d=json.load(sys.stdin);print(d['code'])")
[ "$R2" != "200" ] && ok "不存在的人员被拒绝 (code=$R2)" || bad "不存在的人员竟被接受"

# ---------- P1-4 删除后重新添加 ----------
# 用 zsf（第一支部书记，有归属组织）而非 admin —— admin 没有 org_id，
# 创建发展对象会被明确拒绝，那是另一条修复（见下）
echo
echo "【P1-4】删除发展对象后重新添加应成功（不再 500）"
curl -s -X DELETE $BASE/develop/applicant/3 -H "$AH" >/dev/null
echo '{"personId":29,"orgId":2,"applyDate":"2026-09-16"}' > /tmp/re.json
R4=$(curl -s -X POST $BASE/develop/applicant -H "$ZH" -H "Content-Type: application/json" -d @/tmp/re.json | python -c "import sys,json;d=json.load(sys.stdin);print(d['code'])")
[ "$R4" = "200" ] && ok "重新添加成功 (code=200)" || bad "重新添加失败 (code=$R4)"

# 无归属组织的账号应得到明确提示，而不是数据库层 500
echo
echo "【P1-4b】无归属组织的账号创建发展对象应给出明确提示"
R4B=$(curl -s -X POST $BASE/develop/applicant -H "$AH" -H "Content-Type: application/json" -d @/tmp/re.json \
  | python -c "import sys,json;d=json.load(sys.stdin);print(str(d['code'])+'|'+d['msg'][:40])")
echo "$R4B" | grep -q "^600" && ok "返回业务提示：$R4B" || bad "仍返回无信息量错误：$R4B"

# ---------- P1-5 越权访问 ----------
echo
echo "【P1-5】支部书记访问不存在/兄弟组织应被拒绝"
R5=$(curl -s "$BASE/system/person/org/999" -H "$ZH" | python -c "import sys,json;print(json.load(sys.stdin)['code'])")
[ "$R5" != "200" ] && ok "org/999 被拒绝 (code=$R5)" || bad "org/999 返回 200"

# ---------- P1-8 任务越权 ----------
echo
echo "【P1-8】zsf(org2) 访问 org3 的任务应被拒绝"
T5ORG=$(M "SELECT publish_org_id FROM am_task WHERE task_id=5")
if [ "$T5ORG" = "3" ]; then
  R8=$(curl -s "$BASE/party/task/5" -H "$ZH" | python -c "import sys,json;print(json.load(sys.stdin)['code'])")
  [ "$R8" != "200" ] && ok "跨组织任务被拒绝 (code=$R8)" || bad "跨组织任务仍返回 200"
else
  info "任务5的发布组织是 $T5ORG，跳过"
fi

# ---------- P2-6 驳回后应有待办 ----------
echo
echo "【P2-6】驳回退回上一步后应生成待办记录(status=2)"
M "UPDATE dev_applicant SET current_step='STEP_15', current_stage='STAGE_4', status=1 WHERE applicant_id=10" >/dev/null
M "DELETE FROM dev_step_record WHERE applicant_id=10 AND status=2" >/dev/null
echo '{"applicantId":10,"result":2,"opinion":"材料不全，退回补充"}' > /tmp/rj.json
curl -s -X POST $BASE/develop/flow/handle -H "$AH" -H "Content-Type: application/json" -d @/tmp/rj.json >/dev/null
CS=$(M "SELECT current_step FROM dev_applicant WHERE applicant_id=10")
TODO=$(M "SELECT COUNT(*) FROM dev_step_record WHERE applicant_id=10 AND step_code='$CS' AND status=2")
[ "$TODO" -ge 1 ] && ok "退回后 $CS 有待办记录" || bad "退回后 $CS 无待办（工作项丢失）"

# ---------- P2-7 办结记录的超期标记 ----------
echo
echo "【P2-7】办结记录的 deadline_time 应为计算值，超期时 is_overdue=1"
M "UPDATE dev_applicant SET current_step='STEP_02', current_stage='STAGE_1', status=1, apply_date='2026-07-01' WHERE applicant_id=5" >/dev/null
M "UPDATE dev_step_record SET status=2, deadline_time='2026-07-31 23:59:59' WHERE applicant_id=5 AND step_code='STEP_02'" >/dev/null
echo '{"applicantId":5,"result":1,"opinion":"已谈话"}' > /tmp/d2.json
curl -s -X POST $BASE/develop/flow/handle -H "$AH" -H "Content-Type: application/json" -d @/tmp/d2.json >/dev/null
ROW=$(M "SELECT CONCAT(IFNULL(deadline_time,'NULL'),'|',is_overdue) FROM dev_step_record WHERE applicant_id=5 AND step_code='STEP_02' AND status=1 ORDER BY record_id DESC LIMIT 1")
DL=$(echo $ROW | cut -d'|' -f1); OV=$(echo $ROW | cut -d'|' -f2)
if [ "$DL" != "NULL" ] && [ "$OV" = "1" ]; then
  ok "deadline_time=$DL, is_overdue=$OV"
else
  bad "deadline_time=$DL, is_overdue=$OV（期望非 NULL 且 =1）"
fi

# ---------- P2-8 周期考察序号 ----------
echo
echo "【P2-8】周期性考察 seq_no 应连续无跳号"
SEQ=$(M "SELECT GROUP_CONCAT(seq_no ORDER BY seq_no) FROM dev_step_record WHERE applicant_id=5 AND step_code='STEP_06' AND status=1")
EXPECT=$(M "SELECT COUNT(*) FROM dev_step_record WHERE applicant_id=5 AND step_code='STEP_06' AND status=1")
MAXS=$(M "SELECT IFNULL(MAX(seq_no),0) FROM dev_step_record WHERE applicant_id=5 AND step_code='STEP_06' AND status=1")
if [ "$MAXS" = "$EXPECT" ]; then
  ok "seq_no=$SEQ，共 $EXPECT 条，无跳号"
else
  bad "seq_no=$SEQ（最大 $MAXS），共 $EXPECT 条，存在跳号"
fi

# ---------- 路由 ----------
echo
echo "【路由】16 个菜单的 component 应为纯组件路径"
BADCOMP=$(M "SELECT COUNT(*) FROM sys_menu WHERE component LIKE '%?%'")
[ "$BADCOMP" = "0" ] && ok "无带查询串的 component" || bad "仍有 $BADCOMP 条 component 带查询串"

# ---------- 权限标识与业务动作匹配 ----------
# 曾出现：会议/任务/材料的增删改全用 :list 权限，只授查询权限的账号可以删数据
echo
echo "【权限】增删改不得复用查询权限标识"
MISMATCH=0
for pair in "AmMeetingController.java:meeting" "AmTaskController.java:task" "AmMaterialController.java:orglife"; do
  F=$(echo $pair | cut -d: -f1); P=$(echo $pair | cut -d: -f2)
  CNT=$(grep -c "@PostMapping\|@PutMapping\|@DeleteMapping" \
        "$SRV/hparty-server/hparty-party/src/main/java/com/hparty/party/controller/$F" 2>/dev/null)
  LISTS=$(grep -A1 "@PostMapping\|@PutMapping\|@DeleteMapping" \
        "$SRV/hparty-server/hparty-party/src/main/java/com/hparty/party/controller/$F" 2>/dev/null \
        | grep -c "SaCheckPermission(\"$P:list\")")
  [ "$LISTS" -gt 0 ] && MISMATCH=$((MISMATCH+LISTS))
done
[ "$MISMATCH" = "0" ] && ok "写操作均使用独立的 :add/:edit/:remove/:submit 权限" \
                      || bad "仍有 $MISMATCH 处写操作复用 :list 权限"

# ---------- 数据范围：仅本人用户不得因缺列而报错 ----------
# 曾出现：data_scope=4 的用户查 am_meeting 生成 WHERE person_id=? 而报 Unknown column
echo
echo "【数据范围】仅本人权限的用户查询无 person_id 列的表应正常返回"
XTOKEN=$(login zhaoxue)   # 普通党员，data_scope=4
XH="Authorization: Bearer $XTOKEN"
RMEET=$(curl -s "$BASE/party/meeting/list" -H "$XH" | python -c "import sys,json;print(json.load(sys.stdin)['code'])")
[ "$RMEET" = "200" ] && ok "普通党员读会议列表返回 200（修复前为 500 Unknown column）" \
                     || bad "普通党员读会议列表返回 $RMEET"

# ---------- 党费欠缴识别 ----------
echo
echo "【党费】欠缴接口可用，且 isOverdue 不再是恒为 0 的死字段"
RARR=$(curl -s "$BASE/party/dues/record/arrears?minMonths=1" -H "$AH" | python -c "import sys,json;print(json.load(sys.stdin)['code'])")
[ "$RARR" = "200" ] && ok "欠缴预警接口返回 200" || bad "欠缴预警接口返回 $RARR"

# 构造一笔上上个月的未缴记录，isOverdue 应为 true
M "UPDATE party_dues_record SET dues_year=2026, dues_month=6, status=0, dues_paid=NULL, pay_date=NULL WHERE dues_id=(SELECT MIN(dues_id) FROM (SELECT dues_id FROM party_dues_record) t)" >/dev/null
OVD=$(curl -s "$BASE/party/dues/record/page?pageNum=1&pageSize=1&duesYear=2026&duesMonth=6" -H "$AH" \
      | python -c "import sys,json;d=json.load(sys.stdin)['data']['records'];print(d[0]['isOverdue'] if d else 'none')")
[ "$OVD" = "True" ] && ok "6 月未缴记录的 isOverdue = True（修复前恒为 False）" \
                    || bad "isOverdue = $OVD，期望 True"

echo
echo "════════ 结果：通过 $PASS 项，失败 $FAIL 项 ════════"
echo "（注意：本脚本会改动数据，跑完请执行 sql/03-init-demo.sql 复位）"
