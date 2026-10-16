# Stage B 重构：撤回错误产物、恢复真实门禁

## 用户确认

用户要求“重构处理”，并明确确认完整门禁：独立测试身份、真实入口接通、非空key零读取、原调用透传、一次性采集和报告一致性；全部通过独立检查后才提供设备候选。入口不能安全接通时报告阻断，不再发布“标记已启用、实际无效”的IPA。不自动签名、安装或启动App。

目标仍为Strix1.0.4.12 + 官方iOS1.0.5(201)。数据库直写路线继续获授权，但此阶段只读观察，不改日常库、不读取记录或密钥，不执行引擎或事务。

## 实际失败回归

已撤回的产物：`official-addon/manuscript-lab/build/stage-b-unsigned-public/ManuscriptLab105-StageB-Observer-unsigned.ipa`，历史SHA b44fd88ad9f04571e71bbd1f8be77565a904047b1a23e1a6d97fd9b57353ca8f。

调度者直接读取实际ZIP/Info/MachO，回归返回exit1，三项RED：

- Bundle ID仍为com.rayneo.venus.pub，非隔离身份；
- Runner无@rpath/TurboIOStageBObserver.dylib加载命令；
- schema_observation_enabled=true与加载链矛盾。

原IPA未删除或覆盖，同目录新增REJECTED-DO-NOT-INSTALL.md。此前建议签名安装及自动采集成功的说法撤回。

## 源码缺陷与预测

1. package-stage-b.py在不存在补丁脚本时pass，且不改Bundle ID：如果测试直接反查最终IPA身份与load，必能捕获先前漏检。重构移除所有静默fallback，新目录不得存在，禁止自动rmtree覆盖。
2. StageBObserver的Arm自行设caller_verified为true，HandleOpen不经过真正宿主入口：无真实adapter资格也会读取。必须禁止production伪资格入口，以真实RED验证未经授权不能copy。
3. 集成将key_argument硬传0、用NSDate与重建context重arm：必须通过完整11参数typed透传，密钥非空零观察，并由控制器单次生命周期和monotonicclock约束；不能用直接手调callback的单一正测替代。
4. 编译、模拟fixture或文件存在不证明独立审核／真实入口／运行时隔离。先前task51/52 worker失败无完成证据但被误标完成，以及task53/54/55缺验收完成，现均重开；补task59独立复核。

## 并行任务与范围

- task56：重构StageBObserver/Tests/build脚本及独立stage-b-controller，小Interface、默认BLOCKED_RUNTIME_ENTRY；测试专用capability不进入生产模块，禁止自签trustflag。
- task57：重构package-stage-b.py及stage-b-packaging，固定原IPA完整SHA→可信全成员、独立namespace、强制load及精确postarchive反查，报告linked/entry_installed/active/device_ready分离。
- task58：只读真实入口可行性审查，不patch或运行目标。
- task59：补做SchemaProjection与MemoryCopy的缺失独审，不沿用先前无证完成状态。

这些worker写scope互不重叠，原StageA与f663模块/59e58载体、官方原包和既有未提交文件均保留。

## 真实入口审查结论（task58）

当前实际entry NO-GO。已有Flutter普通lazy dlsym槽只是静态候选：磁盘值是lazyhelper而非realresolver，runtime可写性、lazy竞态、首次lookup时序、caller/handle、返回exactengine入口均未运行确认。普通C嵌套调用real dlsym会改变RTLD_NEXT等caller-sensitive语义，额外lookup／dlerror可污染线程错误；不能改一个RW槽就自称透明。

恢复resolver槽不等于撤回Dart缓存的函数指针；future forwarder必须禁用后持续透明转发并进程生命周期存活，不能承诺live unload。

重签后全文件SHA不是稳定运行身份依据，必须核加载路径/UUID/架构与签名后保留的不可变text等证据；Flutter通过也不能代替Isar引擎身份。无事件必须UNOBSERVED/WAITING_LOOKUP，不能自行发调用诱发或标采集成功。

后续若要真正接通，先需同架构resolver透明性fixture（真实dlopen/dlsym及特殊handle、LR/寄存器/栈与TLS错误），再独立签名运行验证slot/调用时序/原open透传。当前重构只可交内部测试模块和诚实阻断报告；未满足这些条件不提供可用观测IPA。

## 验收记录

正在实现与复核，尚未有新的设备候选；本文件不把将来的测试当成已通过。最终结果另附精确命令、实际退出码、冻结源及产物摘要，原始结果与未知分开。


## 已取得的整改与独审证据

- task59已补做schema projection与memory-copy的真实独审：source、普通测试、ASAN/UBSAN、iOS编译GO，仅纯模块；现依据新证据关闭先前误标51/52，不沿用不存在的closing。
- task56先运行旧真实RED：无adapter的Arm→HandleOpen仍触发copyspy，零读断言失败。重构删除生产旧旁路、自造trustedflags、硬key0、自动写文件；只有HOST TESTING宏可合成测试资格，生产arm始终拒绝。
- task60独审发现测试态clock抛异常持锁，R1用catch终止和finally解锁修复；独立ASAN/UBSAN证明异常后state可达、原函数exactonce、schema零读、不rearm。
- 最终sourceManifest `13811207a48ab3e7b6d8f2a50801ff49e67747d39eeb54ad0da1fa58748e8491`，OUT `stage-b-controller/build/controller-kUim2S`，生产module SHA256 `4897b832a3c89b17ba3def38cc4723bef62b2505f8f55d7999925877986bcf7c`。生产无capture/vmread/engine/resolver/constructor/capmint，GO仅默认BLOCKED_RUNTIME_ENTRY模块。
- 调度者独立重编取得同modulehash，生产拒绝、测试态自有内存读取→投影→原调用、ASAN测试通过；HOST TESTING不作为真实宿主入口证明。
- task57打包器冻结后，调度者独立13项回归PASS：含实际错误IPA拒、原Runner156→157精确追加、缺加载/错bundle/篡改成员拒、未review模块前不建输出。task61现已独审GO，仅当前默认拒绝分支：独立13tests、实际旧包8项RED、可信原包2726文件preflight与伪旁receipt仍blocked/不建输出。source manifest `06222355e5df7f28c6e0435e28c8457b2f15823a2502dfd1fce3f54211678565`，packager SHA `19d93a5cf815a6c5580d57158accfacd8b5d1490807e98f565253eb08f865d6e`。批准表为空，不登记controller、不生成新IPA；未来approved-module完整打包分支未实际运行，不能称全流程验收通过。
- task61独审报告：`/private/var/folders/tr/v96j2p5d123ch_4v42d66mbc0000gn/T/tio-task61-final-x80v7vam/REVIEW.md`。本次只承认已取得的源码与实际拒绝证据，未进入手机运行。

整改代码与拒绝门禁已完成，但真实观测入口仍未安全接通。旧task53/54/55不重新标记成观测已完成；真实设备候选仍待下一独立runtime-entry开发及验收。

当前交付是安全重构源码、实际回归和明确阻断，不以dylib编译或加载命令宣称实际采集就绪。真实entry仍NO-GO，未签名、安装、运行App、采集官方schema或写数据库。
