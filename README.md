# 轻载 (QingZai)

- 抖音无水印下载 Android 客户端。纯原生实现，无需后端服务器，所有逻辑在设备端运行。
- 粘贴分享链接到输入框

## 功能

- **视频下载** — 解析抖音分享链接，提取无水印视频，流式下载到系统相册
- **图文下载** — 解析图文帖子，逐张保存图片到相册（不压缩）
- **动图下载** — 识别动图类型，自动合并背景音乐
- **下载队列** — 后台下载，实时进度显示
- **下载记录** — 持久化存储下载历史，应用重启后可查看
- **历史记录** — 保存解析历史，点击可快速回填
- **Share Intent** — 从抖音 App 直接分享链接到本应用

## 技术栈

| 模块 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM (ViewModel + Repository + UseCase) |
| 依赖注入 | Hilt |
| 网络 | OkHttp |
| 图片加载 | Coil |
| 视频播放 | Media3 ExoPlayer |
| 音视频合并 | FFmpegKit |
| 数据库 | Room |
| 后台任务 | WorkManager |
| 文件存储 | MediaStore (Android 10+) |

## 系统要求

- Android 8.0 (API 26) 及以上

## 构建

```bash
# 使用 Android Studio 自带的 JDK
export JAVA_HOME="/path/to/Android Studio/jbr"

# 编译
./gradlew :app:assembleDebug

# 安装到设备
./gradlew :app:installDebug
```

## 项目结构

```
app/src/main/java/com/douyin/downloader/
├── App.kt                          # Application (Hilt 入口)
├── MainActivity.kt                 # 单 Activity，处理 Share Intent
├── data/
│   ├── local/                      # Room 数据库
│   ├── model/                      # ContentInfo 数据模型
│   ├── remote/                     # 网络请求与 HTML 解析
│   └── repository/                 # 数据仓库
├── di/                             # Hilt 依赖注入模块
├── domain/usecase/                 # 业务用例
├── ui/
│   ├── downloads/                  # 下载中心页面
│   ├── history/                    # 历史记录页面
│   ├── home/                       # 解析主页面
│   ├── navigation/                 # 底部导航框架
│   ├── components/                 # 通用组件
│   └── theme/                      # Material 3 主题
└── worker/                         # WorkManager 后台任务
```

## 许可证

MIT License
