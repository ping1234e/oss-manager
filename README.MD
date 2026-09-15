# OSS 文件管理器

一个基于 Java Swing 的阿里云 OSS 图形化文件管理工具，支持目录浏览、上传下载、批量删除、关键字搜索、复制外链、双击用系统程序打开文件等常用操作。

界面使用 [FlatLaf](https://www.formdev.com/flatlaf/) 现代扁平化外观，风格接近 IntelliJ IDEA。

---

## ✨ 功能特性

- 📂 **目录树浏览**：左侧显示 OSS 一级目录，点击即加载
- 📋 **文件列表**：右侧展示当前目录下的文件与子目录，双击目录进入、双击文件打开
- 🧭 **面包屑导航**：顶部路径导航，任意层级一键跳回
- 🔍 **模糊搜索**：在当前目录（或根目录）递归搜索文件名
- ⬆️ **上传**：支持多选文件、递归上传整个文件夹
- ⬇️ **下载**：选中文件下载到本地任意目录
- 🗑 **批量删除**：多选后一次性删除
- 🔗 **复制外链**：一键生成 OSS 公网 URL 到剪贴板
- ▶️ **系统打开**：双击 MP3 / MP4 / PDF 等文件，下载到本地缓存后调用系统默认程序打开
- ⚙️ **配置外置**：`config.properties` 与 jar 同目录，改配置无需重新编译

---

## 📁 项目结构

```
oss-manager/
├── pom.xml
├── config.properties
├── README.md
└── src/main/java/com/example/ossmanager/
    ├── Main.java
    ├── OssConfig.java
    ├── OssClientWrapper.java
    └── ui/
        └── MainFrame.java
```

---

## 🛠 环境要求

| 项 | 版本 |
|----|------|
| JDK | 1.8 或更高 |
| Maven | 3.6+（推荐 3.9.x） |
| 平台 | Windows / macOS / Linux |

---

## ⚙️ 配置说明

程序启动时会在**同目录**下读取 `config.properties`。如果文件不存在，会自动生成一份默认配置。

```properties
# ========== 阿里云 OSS 配置 ==========
oss.endpoint=XXX
oss.accessKeyId=your-access-key-id
oss.accessKeySecret=your-access-key-secret
oss.bucketName=your-bucket-name

# 静态域名（用于生成公开访问 URL）
oss.staticDomain=XXX

# OSS 上的根目录前缀（仅影响"复制外链"）
# 若 OSS 中文件路径形如 ossfile/xxx.mp3，填 ossfile
# 若文件平铺在 bucket 根目录，留空
oss.rootPrefix=ossfile
```

### 各项含义

| 配置项 | 说明 |
|--------|------|
| `oss.endpoint` | OSS 区域 Endpoint，见阿里云控制台 |
| `oss.accessKeyId` | 阿里云 AccessKey ID |
| `oss.accessKeySecret` | 阿里云 AccessKey Secret |
| `oss.bucketName` | Bucket 名称 |
| `oss.staticDomain` | 绑定的自定义域名，用于拼接外链 |
| `oss.rootPrefix` | OSS 目录前缀，仅用于"复制外链"，不影响浏览/上传/下载 |

---

## 🚀 运行方式

### 方式一：IDE 运行

1. 导入 Maven 项目
2. 在项目根目录创建 `config.properties` 并填好配置
3. 运行 `com.example.ossmanager.Main`

### 方式二：打包后运行

```bash
mvn clean package
```

生成 `target/oss-manager-1.0.0.jar`，拷到任意目录，与 `config.properties` 一起放置：

```
oss-manager/
├── oss-manager-1.0.0.jar
└── config.properties
```

启动：

```bash
java -jar oss-manager-1.0.0.jar
```

---

## 🎯 使用说明

### 浏览文件

- 启动后左侧显示 `📦 <bucket> /` 与一级目录
- **单击左侧目录** → 右侧加载该目录内容
- **双击右侧目录** → 进入下一层
- **双击右侧文件** → 下载到本地缓存并调用系统默认程序打开

### 面包屑导航

顶部路径如 `BUCKET_NAME / > ossfile > Unit 1`，点击任意一级可跳转。

### 搜索

1. 在右上角搜索框输入关键字
2. 按回车或点「搜索」
3. 搜索范围：当前所在目录；未选目录时为根目录
4. 搜索结果中，**双击目录**可进入，**双击文件**可打开
5. 点「清除」恢复普通列表

### 上传

- **上传文件**：单选/多选本地文件，上传到当前目录
- **上传文件夹**：递归上传整个文件夹，保留目录结构

### 下载

- 选中一个或多个文件 → 点「下载」 → 选择保存目录

### 删除

- 选中文件 → 点「删除」 → 二次确认后批量删除

### 复制外链

- 选中文件 → 点「复制链接」 → URL 已复制到剪贴板

### 打开配置

- 点「打开配置」按钮 → 使用系统默认编辑器打开 `config.properties`

---

## 🖼 界面一览

```
┌──────────────────────────────────────────────────────────────┐
│  OSS 文件管理器 - BUCKET_NAME                              — □ ×  │
├──────────────────────────────────────────────────────────────┤
│  [上传文件][上传文件夹][刷新] [下载][删除][复制链接] [打开配置] │
│  搜索: [输入文件名关键字...            ✕] [搜索][清除]        │
├──────────────┬───────────────────────────────────────────────┤
│ 📦 BUCKET_NAME / │  BUCKET_NAME / > ossfile > 搜索: period            │
│   📁 ossfile ├───────────────────────────────────────────────┤
│              │  名称                    │ 类型 │ 大小 │ 修改  │
│              │  📄 Unit 1 Period 1.mp3  │ 文件 │ 3.2M │ ...   │
│              │  📄 Unit 1 Period 2.mp3  │ 文件 │ 2.1M │ ...   │
├──────────────┴───────────────────────────────────────────────┤
│  搜索完成: 匹配 2 项    关键字: period    范围: ossfile/     │
└──────────────────────────────────────────────────────────────┘
```

---

## 📦 依赖

| 依赖 | 用途 |
|------|------|
| `com.aliyun.oss:aliyun-sdk-oss` | 阿里云 OSS SDK |
| `com.formdev:flatlaf` | 现代扁平化 Swing 外观 |
| `org.slf4j:slf4j-api` | 日志门面 |
| `ch.qos.logback:logback-classic` | 日志实现 |

---

## 🐛 常见问题

### 1. 无法打开 MP3 / MP4

- 确认系统安装了对应的默认播放器
- Linux 环境若缺少 `Desktop` 支持，会提示"当前系统不支持打开文件"

### 2. 复制的外链打不开（404）

- 检查 `oss.rootPrefix` 配置是否与 OSS 实际路径一致
- 若 OSS 中文件是扁平的（无目录前缀），把 `oss.rootPrefix` 留空

### 3. 搜索很慢

- OSS 无服务端模糊搜索，只能遍历对象后客户端过滤
- 建议缩小搜索范围：先进到具体目录再搜索

---

## 📝 开发规范

- 遵循阿里巴巴 Java 开发手册
- 关键路径使用 SLF4J 记录日志，禁止 `System.out.println`
- 常量抽取为 `private static final`，禁止魔法值
- 集合初始化指定容量
- 使用 try-with-resources 管理资源

---

## 📄 License

MIT License

---

## 🙋 反馈

遇到问题或有功能建议，欢迎提交 Issue 或 PR。