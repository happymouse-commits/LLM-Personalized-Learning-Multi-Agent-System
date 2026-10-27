/**
 * 师承智导 演示模式：统一 Mock 数据
 * 所有数据预置，无需后端即可展示完整前端效果
 */
export const demoUser = {
  id: 1,
  role: 'student',
  username: 'demo_student',
  nickname: '小明',
  token: 'demo_token_12345',
  className: '软件工程2101',
  studentId: '2021001'
}

export const demoProfile = {
  knowledgeLevel: 62,
  cognitiveStyle: 'visual',
  learningPreference: 'exercise',
  learningPace: 'steady',
  interestDirection: '嵌入式开发、游戏编程',
  studyMotivation: 'extrinsic',
  focusLevel: 'medium',
  weakAreas: '指针,内存管理,结构体,递归',
  progress: 45,
  totalStudyMinutes: 340,
  totalQuestions: 28,
  streakDays: 7,
  profileUpdatedAt: '2026-06-23T10:30:00'
}

export const demoResources = [
  // ===== 基础语法章节资源 =====
  { id: 201, type: 'article', title: 'C语言基础语法入门', knowledgePoint: '基础语法', content: '# C语言基础语法入门\n\n## 一、C语言程序结构\n\n每个C程序都由一个或多个函数组成，其中**必须有一个 `main` 函数**作为程序的入口点。一个最简单的C程序如下：\n\n```c\n#include <stdio.h>\n\nint main() {\n    printf("Hello, World!\\n");\n    return 0;\n}\n```\n\n程序结构解析：\n- `#include <stdio.h>`：预处理器指令，引入标准输入输出头文件，让程序可以使用 `printf` 和 `scanf` 等函数\n- `int main()`：主函数声明，`int` 表示返回整型值，`main` 是函数名，括号内可放参数\n- `{ }`：花括号包围函数体，所有可执行语句必须放在花括号内\n- `printf("Hello, World!\\n");`：调用标准输出函数，将字符串打印到屏幕，`\\n` 表示换行\n- `return 0;`：向操作系统返回退出码，`0` 表示程序正常退出，非零值表示异常\n\n## 二、数据类型\n\nC语言是**强类型语言**，每个变量在使用前必须先声明其类型。以下是常用基本数据类型：\n\n| 类型 | 关键字 | 占用字节 | 取值范围 | 格式符 |\n|------|--------|----------|----------|--------|\n| 整型 | `int` | 4 | -2,147,483,648 ~ 2,147,483,647 | `%d` |\n| 短整型 | `short` | 2 | -32,768 ~ 32,767 | `%hd` |\n| 长整型 | `long` | 4/8 | 取决于平台 | `%ld` |\n| 长长整型 | `long long` | 8 | -9.2e18 ~ 9.2e18 | `%lld` |\n| 单精度浮点 | `float` | 4 | 约 ±3.4e-38 ~ ±3.4e+38 | `%f` |\n| 双精度浮点 | `double` | 8 | 约 ±1.7e-308 ~ ±1.7e+308 | `%lf` |\n| 字符型 | `char` | 1 | -128 ~ 127 或 0 ~ 255 | `%c` |\n\n> **提示**：以上字节数以64位系统为例，实际大小可通过 `sizeof` 运算符获取。\n\n### 类型修饰符\n\n```c\nunsigned int a = 100;   // 无符号，范围 0 ~ 4,294,967,295\nsigned int b = -100;    // 有符号（默认，signed 可省略）\nshort int c = 10;       // 短整型（short int 可简写为 short）\nlong int d = 100000L;   // 长整型（long int 可简写为 long）\n```\n\n- `unsigned`：无符号，将负数范围映射到正数，适用于年龄、计数等非负数场景\n- `signed`：有符号（默认值，通常省略不写）\n- `short` / `long`：改变存储长度，`short` 不短于 `int`，`long` 不长于 `int`\n\n### sizeof 运算符\n\n`sizeof` 是编译时运算符，用于获取变量或类型占用的字节数：\n\n```c\n#include <stdio.h>\n\nint main() {\n    printf("int: %zu 字节\\n", sizeof(int));\n    printf("char: %zu 字节\\n", sizeof(char));\n    printf("double: %zu 字节\\n", sizeof(double));\n    printf("short: %zu 字节\\n", sizeof(short));\n    printf("long long: %zu 字节\\n", sizeof(long long));\n\n    int a = 10;\n    printf("变量a: %zu 字节\\n", sizeof(a));\n    printf("int数组[10]: %zu 字节\\n", sizeof(int[10]));\n    return 0;\n}\n```\n\n## 三、变量与常量\n\n### 变量的声明与初始化\n\n```c\nint age;                   // 声明但未初始化（局部变量值为随机值，全局变量默认0）\nint score = 95;            // 声明并初始化，推荐始终初始化\nfloat pi = 3.14159f;       // float 字面量加 f 后缀\nchar grade = \'A\';          // 字符用单引号\n\n// 多变量同时声明\nint a = 1, b = 2, c = 3;\nint x, y, z;               // 也可分开声明，更清晰\n```\n\n### 变量命名规则\n\n1. 只能包含**字母**（a-z、A-Z）、**数字**（0-9）和**下划线**（`_`）\n2. 不能以数字开头（如 `1var` 非法）\n3. 区分大小写（`age`、`Age`、`AGE` 是三个不同的变量）\n4. 不能使用C语言关键字（如 `int`、`if`、`return`、`while` 等32个保留字）\n5. 建议使用有意义的英文命名，如 `studentAge`、`totalScore`、`maxValue`\n\n### 变量作用域\n\n```c\n#include <stdio.h>\n\nint globalVar = 100;       // 全局变量：整个文件可见，程序启动时创建，结束时销毁\n\nvoid test() {\n    printf("全局变量: %d\\n", globalVar);  // 可以在任何函数中访问\n}\n\nint main() {\n    int localVar = 50;     // 局部变量：仅在 main 函数内可见\n    printf("局部变量: %d\\n", localVar);\n\n    {\n        int blockVar = 10;  // 块级变量：仅在当前代码块 { } 内可见\n        printf("块级变量: %d\\n", blockVar);\n    }\n    // blockVar 在此处已不可见，编译器会报错\n\n    test();\n    return 0;\n}\n```\n\n### 常量定义\n\n**方法一：`#define` 宏常量**\n\n```c\n#define PI 3.1415926\n#define MAX_STUDENTS 50\n#define GREETING "Hello, World"\n\n// 使用\nfloat area = PI * radius * radius;\nint class[MAX_STUDENTS];\nprintf("%s\\n", GREETING);\n```\n\n- 预处理器在编译前进行**纯文本替换**，不分配内存\n- 无类型检查，可能出现意外替换\n- 习惯用**全大写+下划线**命名\n\n**方法二：`const` 常变量**\n\n```c\nconst double PI = 3.1415926;\nconst int MAX_STUDENTS = 50;\n\n// PI = 3.14;  // 错误！const 变量不可修改\n```\n\n- 有类型检查，分配内存，更加安全\n- 声明后不可修改，编译器会阻止意外赋值\n- **推荐使用 const 替代 #define 定义常量**\n\n## 四、运算符\n\n### 算术运算符\n\n| 运算符 | 含义 | 示例 | 结果 | 注意 |\n|--------|------|------|------|------|\n| `+` | 加法 | `5 + 3` | 8 | — |\n| `-` | 减法 | `5 - 3` | 2 | — |\n| `*` | 乘法 | `5 * 3` | 15 | — |\n| `/` | 除法 | `5 / 2` | 2 | 整数除法截断小数 |\n| `%` | 取余（模） | `5 % 2` | 1 | 只能用于整数 |\n\n> **整数除法陷阱**：`5 / 2` 结果为 `2`（不是 `2.5`），因为两个 `int` 相除结果仍为 `int`。解决方式：`(float)5 / 2` 或 `5.0 / 2`。\n\n### 自增自减运算符\n\n```c\nint a = 5;\nint b = ++a;  // 前置自增：a 先加1变为6，再赋值给 b，结果 a=6, b=6\nint c = a++;  // 后置自增：a(6) 先赋值给 c，a 再加1，结果 c=6, a=7\n\nint d = 5;\nint e = --d;  // 前置自减：d=4, e=4\nint f = d--;  // 后置自减：f=4, d=3\n```\n\n> **建议**：避免在复杂表达式中混用自增运算符，容易产生未定义行为。\n\n### 关系运算符\n\n| 运算符 | 含义 | 示例 | 返回值 |\n|--------|------|------|--------|\n| `==` | 等于 | `5 == 3` | 0（假） |\n| `!=` | 不等于 | `5 != 3` | 1（真） |\n| `>` | 大于 | `5 > 3` | 1（真） |\n| `<` | 小于 | `5 < 3` | 0（假） |\n| `>=` | 大于等于 | `5 >= 5` | 1（真） |\n| `<=` | 小于等于 | `5 <= 3` | 0（假） |\n\n> **致命错误**：将 `==`（等于判断）误写为 `=`（赋值）：`if (a = 5)` 永远为真，因为赋值表达式 `a = 5` 的值是 `5`（非零即为真）。\n\n### 逻辑运算符\n\n| 运算符 | 含义 | 短路特性 | 示例 |\n|--------|------|----------|------|\n| `&&` | 逻辑与 | 左边为假时右边不再执行 | `a > 0 && b > 0` |\n| `\\|\\|` | 逻辑或 | 左边为真时右边不再执行 | `a > 0 \\|\\| b > 0` |\n| `!` | 逻辑非 | 取反 | `! (a > 0)` |\n\n短路特性示例：\n\n```c\nint a = 0;\nif (a != 0 && 10 / a > 5) {\n    // 由于 a==0，左边为假，右边 10/a 不会执行，避免了除零错误\n}\n\nint b = 5;\nif (b > 0 || b / 0 > 1) {\n    // 由于 b>0 为真，右边 b/0 不会执行\n}\n```\n\n### 位运算符（进阶）\n\n| 运算符 | 含义 | 示例 | 说明 |\n|--------|------|------|------|\n| `&` | 按位与 | `5 & 3 = 1` | `0101 & 0011 = 0001` |\n| `\\|` | 按位或 | `5 \\| 3 = 7` | `0101 \\| 0011 = 0111` |\n| `^` | 按位异或 | `5 ^ 3 = 6` | `0101 ^ 0011 = 0110` |\n| `~` | 按位取反 | `~5 = -6` | `~0000...0101 = 1111...1010` |\n| `<<` | 左移 | `5 << 1 = 10` | 相当于乘以 2 |\n| `>>` | 右移 | `5 >> 1 = 2` | 相当于除以 2 |\n\n### 运算符优先级（从高到低）\n\n1. `()` `[]` `->` `.`（最高）\n2. `!` `~` `++` `--` `+`（正）`-`（负）`*`（解引用）`&`（取地址）`sizeof`\n3. `*` `/` `%`（乘除取余）\n4. `+` `-`（加减）\n5. `<<` `>>`（移位）\n6. `<` `<=` `>` `>=`（关系）\n7. `==` `!=`（相等）\n8. `&`（按位与）\n9. `^`（按位异或）\n10. `|`（按位或）\n11. `&&`（逻辑与）\n12. `||`（逻辑或）\n13. `=` `+=` `-=` 等赋值运算符（最低）\n\n> **金科玉律**：不确定优先级时，用括号 `()` 明确运算顺序，既安全又提高可读性。\n\n## 五、输入输出\n\n### printf 格式化输出\n\n```c\n#include <stdio.h>\n\nint main() {\n    int age = 20;\n    float score = 95.5;\n    char grade = \'A\';\n\n    printf("年龄：%d 岁\\n", age);\n    printf("成绩：%.1f 分\\n", score);          // .1 表示保留1位小数\n    printf("等级：%c\\n", grade);\n    printf("地址：%p\\n", &age);                // 打印变量地址\n    printf("十六进制：%x\\n", 255);             // 输出 ff\n    printf("百分号：100%%\\n");                 // 输出百分号本身\n    printf("左对齐：%-10d右对齐\\n", 42);      // 宽度10，左对齐\n    printf("补零：%05d\\n", 42);               // 宽度5，前面补0\n    return 0;\n}\n```\n\n### scanf 格式化输入\n\n```c\n#include <stdio.h>\n\nint main() {\n    int age;\n    float score;\n    char name[20];\n\n    printf("请输入姓名：");\n    scanf("%s", name);          // 字符串不加 &，数组名本身就是地址\n\n    printf("请输入年龄：");\n    scanf("%d", &age);          // 整型变量必须加 &\n\n    printf("请输入成绩：");\n    scanf("%f", &score);        // 浮点型变量也必须加 &\n\n    printf("\\n--- 学生信息 ---\\n");\n    printf("姓名：%s\\n", name);\n    printf("年龄：%d\\n", age);\n    printf("成绩：%.1f\\n", score);\n    return 0;\n}\n```\n\n> **scanf 三大注意事项**：\n> 1. **变量前必须加 `&`**（取地址符），否则会导致段错误或数据写入错误内存位置\n> 2. `scanf("%s", str)` 遇到空格会停止读取，用 `fgets()` 读取含空格的字符串\n> 3. 读取字符后缓冲区可能残留换行符，用 `while(getchar() != \'\\n\');` 清除\n\n## 六、类型转换\n\n### 隐式转换（自动类型提升）\n\nC语言在运算时自动将"小"类型转换为"大"类型，规则为：`char → int → long → float → double`\n\n```c\nint a = 5;\ndouble b = 2.5;\ndouble result = a + b;  // a 自动转换为 double，结果为 7.5\n\nchar c = \'A\';           // ASCII 值为 65\nint d = c + 1;           // c 自动转为 int，结果为 66\n```\n\n### 强制类型转换（显式转换）\n\n```c\nint a = 5, b = 2;\nfloat result1 = (float)a / b;   // 先将 a 转为 float，结果 2.5\nfloat result2 = a / (float)b;   // 等价写法\nfloat result3 = (float)(a / b); // 错误！先整数除法得2，再转float得2.0\n\n// 常用场景：避免整数除法截断\nprintf("%.2f\\n", (double)10 / 3);  // 输出 3.33\n```\n\n## 七、常见错误与调试\n\n| 序号 | 错误类型 | 错误代码 | 正确代码 | 说明 |\n|------|----------|----------|----------|------|\n| 1 | 忘记分号 | `printf("hello")` | `printf("hello");` | 每条语句以 `;` 结尾 |\n| 2 | scanf 漏 & | `scanf("%d", a)` | `scanf("%d", &a)` | 必须传地址 |\n| 3 | 整数除法 | `float f = 1/2;` | `float f = 1.0/2;` | 结果为 0.0，不是 0.5 |\n| 4 | = 与 == 混淆 | `if (a = 5)` | `if (a == 5)` | 赋值永远为真 |\n| 5 | 格式符不匹配 | `printf("%f", 10)` | `printf("%d", 10)` | 导致未定义行为 |\n| 6 | 字符与字符串 | `if (c == "y")` | `if (c == \'y\')` | 单引号字符，双引号字符串 |\n| 7 | 中文标点 | `printf("你好");` | `printf("你好");` | 必须使用英文标点符号 |\n| 8 | 数组越界 | `arr[10]`（长度10） | `arr[9]`（最大下标） | 下标 0~9，不是 1~10 |\n\n## 八、练习建议\n\n### 基础题（必做）\n\n1. **Hello World 变体**：编写程序分别打印你的姓名、学号、班级\n2. **圆面积计算**：输入半径 r，计算并输出圆的面积（π×r²）和周长（2×π×r）\n3. **温度转换**：输入摄氏温度 C，转换为华氏温度 F = C × 9/5 + 32\n4. **数字拆分**：输入一个三位数，分别输出其百位、十位、个位数字\n5. **数据类型大小**：用 `sizeof` 打印所有基本数据类型在当前平台上的字节数\n\n### 进阶题（选做）\n\n6. **简单计算器**：输入两个数和运算符（+ - * /），输出计算结果\n7. **三数最值**：输入三个整数，找出并输出最大值和最小值\n8. **字符加密**：输入一个字符，输出其 ASCII 码偏移 3 位后的字符\n\n### 挑战题\n\n9. **进制转换**：输入一个十进制整数，输出其二进制、八进制、十六进制表示\n10. **时间换算**：输入总秒数，转换为"时:分:秒"格式输出', difficulty: 'easy', generatedBy: 'auto-pipeline', hallucinationScore: 0.95, hallucinationRisk: '低风险', createdAt: '2026-07-14T10:00:00' },
  { id: 202, type: 'explanation', title: 'C语言注释与编码规范详解', knowledgePoint: '基础语法', content: '# C语言注释与编码规范详解\n\n## 一、注释的重要性\n\n注释是代码中**不被编译器执行**的说明性文字，用来解释代码的意图、逻辑和注意事项。良好的注释习惯是专业程序员的必备素养。\n\n> **核心理念**：代码告诉你"做了什么"，注释告诉你"为什么这么做"。\n\n## 二、单行注释\n\n### 基本语法\n\n单行注释以 `//` 开头，从 `//` 开始到本行末尾的所有内容都被视为注释：\n\n```c\n#include <stdio.h>\n\nint main() {\n    int age = 20;                // 声明并初始化年龄变量\n    float score = 95.5;          // 声明并初始化成绩变量\n\n    // 打印学生信息\n    printf("年龄：%d\\n", age);\n    printf("成绩：%.1f\\n", score);\n\n    // TODO: 后续需要添加输入验证\n    // FIXME: 此处应先检查成绩范围是否合法\n\n    return 0;                    // 程序正常退出\n}\n```\n\n### 使用场景\n\n1. **行尾注释**：对当前行代码做简短说明\n2. **独立行注释**：解释下面一段代码的逻辑\n3. **标记注释**：使用 `TODO`、`FIXME`、`HACK`、`NOTE` 等标记\n4. **临时禁用代码**：调试时快速注释掉某行代码\n\n```c\nint x = 10;\n// int y = 20;    // 临时禁用这行，方便调试\nint z = x + 5;\nprintf("z = %d\\n", z);\n```\n\n### 常见标记约定\n\n| 标记 | 含义 | 使用场景 |\n|------|------|----------|\n| `TODO` | 待完成 | 功能尚未实现，需要后续补充 |\n| `FIXME` | 需修复 | 已知有 bug，需要修复 |\n| `HACK` | 临时方案 | 用了不够优雅的写法，需要重构 |\n| `NOTE` | 注意事项 | 需要特别留意的细节 |\n| `XXX` | 警告 | 代码有潜在风险 |\n\n## 三、多行注释\n\n### 基本语法\n\n多行注释以 `/*` 开头，以 `*/` 结尾，可以跨越多行，中间的所有内容都是注释：\n\n```c\n/*\n * 程序名称：学生信息管理系统\n * 作者：小明\n * 日期：2026-07-14\n * 版本：v1.0\n * 功能：实现学生信息的录入、查询、修改和删除\n */\n\n#include <stdio.h>\n\nint main() {\n    /*\n     * 第一步：初始化数据结构\n     * 第二步：显示主菜单\n     * 第三步：根据用户选择执行相应操作\n     */\n\n    printf("欢迎使用学生信息管理系统！\\n");\n\n    /* 这是单行的多行注释写法 */\n    int choice = 0;\n\n    return 0;\n}\n```\n\n### 使用场景\n\n1. **文件头注释**：描述文件的整体功能、作者、版本信息\n2. **函数头注释**：说明函数的功能、参数、返回值\n3. **段落注释**：解释一段较长的代码逻辑\n4. **大段代码注释**：临时禁用多行代码\n\n### 注意事项\n\n```c\n/*\n * 多行注释不能嵌套！\n * /* 这是非法的嵌套注释 */   ← 编译器会报错\n */\n\n// 正确的做法：外层用 /* */，内层用 //\n/*\n * 内层注释使用单行方式\n * // 这样可以避免嵌套问题\n */\n```\n\n> **重要**：C语言的多行注释 **不支持嵌套**。如果在 `/* */` 内部再出现 `/*`，编译器会把第一个 `*/` 当作注释结束，导致后面的代码被意外注释掉。\n\n## 四、文档注释风格\n\n虽然不是C语言标准的一部分，但以下两种文档注释风格在大型项目中广泛使用：\n\n### Doxygen 风格（推荐）\n\n```c\n/**\n * @brief 计算两个整数的最大公约数\n *\n * 使用辗转相除法（欧几里得算法）计算最大公约数。\n * 算法复杂度为 O(log min(a, b))。\n *\n * @param a 第一个正整数\n * @param b 第二个正整数\n * @return a 和 b 的最大公约数\n *\n * @note 参数必须为正整数，否则行为未定义\n * @see lcm() 最小公倍数函数\n */\nint gcd(int a, int b) {\n    while (b != 0) {\n        int temp = b;\n        b = a % b;\n        a = temp;\n    }\n    return a;\n}\n```\n\n### 常用 Doxygen 标签\n\n| 标签 | 含义 | 示例 |\n|------|------|------|\n| `@brief` | 简要描述 | `@brief 计算阶乘` |\n| `@param` | 参数说明 | `@param n 输入整数` |\n| `@return` | 返回值说明 | `@return 阶乘结果` |\n| `@note` | 注意事项 | `@note 仅支持 n≤20` |\n| `@see` | 参见 | `@see factorial()` |\n| `@warning` | 警告 | `@warning 未检查溢出` |\n| `@author` | 作者 | `@author 小明` |\n| `@date` | 日期 | `@date 2026-07-14` |\n| `@version` | 版本 | `@version 1.0` |\n\n## 五、编码规范\n\n### 1. 命名规则\n\n#### 变量命名\n\n| 风格 | 示例 | 适用场景 |\n|------|------|----------|\n| 小驼峰（camelCase） | `studentAge`、`totalScore`、`maxValue` | 局部变量 |\n| 下划线（snake_case） | `student_age`、`total_score`、`max_value` | 局部变量、C标准库风格 |\n| 全大写+下划线 | `MAX_STUDENTS`、`PI`、`BUFFER_SIZE` | 宏常量、全局常量 |\n\n```c\n// 推荐：见名知意\nint studentAge;           // ✅ 清晰表达含义\nfloat finalExamScore;     // ✅ 明确是期末考试成绩\n\n// 不推荐\nint a;                    // ❌ 含义不明\nfloat f;                  // ❌ 不知道 f 代表什么\nint tmp;                  // ❌ 可以用，但更具体会更好\n```\n\n#### 函数命名\n\n函数名通常使用**动词开头**，描述函数执行的动作：\n\n| 前缀 | 含义 | 示例 |\n|------|------|------|\n| `get` | 获取 | `getStudentAge()`、`getMaxValue()` |\n| `set` | 设置 | `setGrade()`、`setName()` |\n| `is` | 判断（返回布尔） | `isPrime()`、`isValid()` |\n| `calc`/`compute` | 计算 | `calcAverage()`、`computeSum()` |\n| `print`/`display` | 显示 | `printMenu()`、`displayResult()` |\n| `init` | 初始化 | `initArray()`、`initStruct()` |\n| `create`/`destroy` | 创建/销毁 | `createNode()`、`destroyList()` |\n\n```c\n// 推荐命名\nint calculateSum(int arr[], int n);\nvoid printStudentInfo(Student *s);\nbool isLeapYear(int year);\nvoid initArray(int arr[], int n, int value);\n\n// 不推荐命名\nint func1(int a[], int b);      // ❌ 名字无意义\nvoid p(Student *s);             // ❌ 太短，无法理解\nbool year(int y);               // ❌ 缺少动词\n```\n\n### 2. 缩进与空格\n\n```c\n// 推荐：使用 4 个空格缩进（或 1 个 Tab）\nint main() {\n    int a = 10;\n\n    if (a > 0) {\n        printf("正数\\n");\n        if (a > 100) {\n            printf("大于100\\n");\n        }\n    }\n\n    for (int i = 0; i < 10; i++) {\n        printf("%d ", i);\n    }\n\n    return 0;\n}\n\n// 在运算符两侧加空格\nint result = a + b * c;        // ✅ 清晰\nint result=a+b*c;              // ❌ 拥挤\n\n// 逗号后面加空格\nint a = 1, b = 2, c = 3;       // ✅\nint a=1,b=2,c=3;               // ❌\n```\n\n> **关键原则**：整个项目统一使用一种缩进风格，不要混用空格和Tab。\n\n### 3. 括号风格\n\nC语言中主要有两种括号风格：\n\n#### K&R 风格（推荐）\n\n```c\n// 左括号放在行尾，右括号独占一行\nint main() {\n    if (a > 0) {\n        printf("正数\\n");\n    } else {\n        printf("非正数\\n");\n    }\n\n    for (int i = 0; i < 10; i++) {\n        printf("%d ", i);\n    }\n}\n```\n\n#### Allman 风格\n\n```c\n// 每个括号独占一行\nint main()\n{\n    if (a > 0)\n    {\n        printf("正数\\n");\n    }\n    else\n    {\n        printf("非正数\\n");\n    }\n}\n```\n\n两种风格都可以，关键是**保持一致性**。K&R 风格在C语言社区中更为常见。\n\n### 4. 每行代码长度\n\n建议每行代码不超过 **80~100** 个字符。如果一行太长，应该换行：\n\n```c\n// 长函数调用换行\nprintf("学生姓名：%s，年龄：%d，成绩：%.1f，排名：%d/%d\\n",\n       student.name, student.age, student.score,\n       student.rank, student.total);\n\n// 长表达式换行\nint result = (a * b + c * d) / (e * f)\n             + (g * h) / (i * j);\n\n// 长条件判断换行\nif (age >= 18 && age <= 60\n    && score >= 60\n    && isRegistered == true) {\n    printf("符合条件\\n");\n}\n```\n\n## 六、常见编码误区\n\n### 1. 过度注释\n\n```c\n// ❌ 废话注释，没有提供任何额外信息\nint age = 20;           // 将 age 赋值为 20\nprintf("hello");        // 打印 hello\nreturn 0;               // 返回 0\n\n// ✅ 好的注释解释"为什么"\nint age = 20;           // 默认年龄设为20（符合系统最小年龄要求）\nprintf("hello");        // 打印欢迎信息\nreturn 0;               // 返回0表示程序正常退出\n```\n\n### 2. 注释与代码不一致\n\n```c\n// ❌ 注释说返回最大值，但实际返回的是最小值\nint getMaxValue(int a, int b) {\n    return a < b ? a : b;  // 实际返回的是最小值！\n}\n\n// ✅ 修改代码后务必同步更新注释\nint getMinValue(int a, int b) {\n    return a < b ? a : b;\n}\n```\n\n### 3. 用注释替代清晰的代码\n\n```c\n// ❌ 靠注释来解释糟糕的命名\nint d;  // 天数\nint m;  // 月份\nint y;  // 年份\n\n// ✅ 直接用清晰的命名，无需注释\nint days;\nint month;\nint year;\n```\n\n### 4. 注释掉的大段代码\n\n```c\n// ❌ 不要保留大段被注释的旧代码\n/*\nint oldFunction() {\n    // ... 100 行旧代码\n}\n*/\n\n// ✅ 使用版本控制系统（如 Git）管理历史代码，直接删除不要的代码\n```\n\n## 七、最佳实践总结\n\n### 必须写注释的场合\n\n1. **复杂的算法逻辑**：解释为什么选择这个算法，时间/空间复杂度\n2. **非直观的代码**：有违直觉的写法，需要说明原因\n3. **边界条件处理**：特殊的边界情况处理逻辑\n4. **公共 API 函数**：函数的功能、参数、返回值、异常情况\n5. **重要常量**：魔数的含义和来源\n\n### 不需要写注释的场合\n\n1. 代码本身已经足够清晰表达了意图\n2. 变量名和函数名已经准确描述了含义\n3. 重复代码本体的信息（废话注释）\n\n### 黄金法则\n\n> **好代码 > 好注释 > 坏代码 + 注释 > 坏代码**\n> \n> 最好的注释就是不需要注释的代码——用清晰的命名和结构让代码自解释。\n\n### 注释检查清单\n\n- [ ] 每个 `.c` 文件开头有文件头注释\n- [ ] 每个公开函数有函数头注释（参数、返回值、功能说明）\n- [ ] 复杂算法有步骤说明注释\n- [ ] 魔数（Magic Number）有含义说明\n- [ ] 没有注释掉的大段旧代码\n- [ ] 没有过时的、与代码不一致的注释\n- [ ] 注释语言清晰、简洁、专业', difficulty: 'easy', generatedBy: 'auto-pipeline', hallucinationScore: 0.96, createdAt: '2026-07-14T10:00:00' },
  { id: 203, type: 'code', title: '基础语法综合示例', knowledgePoint: '基础语法', content: '## 问题描述\n使用 C 语言演示基础语法的综合用法：变量声明与初始化、格式化输出 printf、以及常见算术运算符的使用。\n\n## 输入格式\n无输入，程序运行后直接输出内置变量与运算结果。\n\n## 输出格式\n按顺序输出以下内容：\n- 变量 age、score、grade 的格式化打印\n- 算术运算 a+b、a/b（整除）、a%b（取余）的结果\n\n## 样例输入 1\n```plaintext\n（无输入）\n```\n\n## 样例输出 1\n```plaintext\n年龄：20\n成绩：95.5\n等级：A\na + b = 13\na / b = 3\na % b = 1\n```\n\n## 代码解析\n本程序覆盖了 C 语言入门最核心的 4 个语法点：\n1. **变量声明与初始化**：int（整型）/float（单精度浮点）/char（字符型）三种基本类型，定义同时赋初值\n2. **格式化输出 printf**：`%d` 对应整型，`%.1f` 对应保留 1 位小数的浮点，`%c` 对应字符，`\\n` 换行\n3. **算术运算符**：`+` 加法、`/` 整除（两 int 相除结果仍为 int，小数部分截断）、`%` 取余（仅用于整型）\n4. **main 函数结构**：所有 C 程序都从 `main()` 开始执行，`return 0` 表示正常退出\n\n```c\n#include <stdio.h>\n\nint main() {\n    // 变量声明与初始化\n    int age = 20;\n    float score = 95.5;\n    char grade = \'A\';\n\n    // 格式化输出\n    printf(\"年龄：%d\\n\", age);\n    printf(\"成绩：%.1f\\n\", score);\n    printf(\"等级：%c\\n\", grade);\n\n    // 运算符示例\n    int a = 10, b = 3;\n    printf(\"a + b = %d\\n\", a + b);\n    printf(\"a / b = %d\\n\", a / b);\n    printf(\"a %% b = %d\\n\", a % b);\n\n    return 0;\n}\n```\n\n## 改进建议\n可使用 scanf 让用户输入 age、score、a、b 的值，使程序更加通用；输出字符 `%` 时要注意使用 `%%` 转义，单个 `%` 会被当作格式符起始位导致未定义行为。', difficulty: 'easy', generatedBy: 'auto-pipeline', hallucinationScore: 0.98, compileStatus: 'PASSED', createdAt: '2026-07-14T10:00:00' },
  { id: 204, type: 'exercise', title: '基础语法练习题', knowledgePoint: '基础语法', content: '# 基础语法练习\n\n1. 以下哪个是合法的变量名？\n2. printf(\"%d\", 10/3) 的输出是什么？\n3. 如何交换两个变量的值？', difficulty: 'easy', generatedBy: 'auto-pipeline', hallucinationScore: 0.95, createdAt: '2026-07-14T10:00:00' },
  { id: 206, type: 'mindmap', title: '基础语法知识图谱', knowledgePoint: '基础语法', content: '# C语言基础语法\n## 数据类型\n### 基本类型\n- int 整型（4字节）\n- float 单精度浮点（4字节）\n- double 双精度浮点（8字节）\n- char 字符型（1字节）\n### 修饰符\n- short 短整型\n- long 长整型\n- unsigned 无符号\n- signed 有符号（默认）\n### 类型转换\n- 隐式转换（小→大）\n- 强制转换（大→小，可能丢失精度）\n## 运算符\n### 算术运算\n- + - * / %\n- 自增++ / 自减--\n- 前置与后置的区别\n### 关系运算\n- == != > < >= <=\n- 注意=与==的区别\n### 逻辑运算\n- && 与 || 或 ! 非\n- 短路求值机制\n### 位运算\n- & | ^ ~ << >>\n- 异或的常见应用：交换变量\n## 输入输出\n### printf格式化输出\n- %d %f %c %s %p\n- 宽度与精度控制\n- printf返回值含义\n### scanf格式化输入\n- 必须传地址（&变量）\n- 缓冲区残留问题\n- 返回值表示成功读取个数\n## 变量与常量\n### 变量\n- 声明与定义\n- 初始化的重要性\n- 作用域规则\n### 常量\n- #define 宏常量\n- const修饰的常变量\n- 枚举常量enum\n## 编码规范\n### 命名规则\n- 小驼峰/下划线风格\n- 见名知意原则\n### 注释\n- // 单行注释\n- /* */ 多行注释\n- 注释的误区：不要写废话', difficulty: 'easy', generatedBy: 'auto-pipeline', hallucinationScore: 0.97, createdAt: '2026-07-14T10:00:00' },

  // ===== 流程控制章节资源 =====
  { id: 211, type: 'article', title: 'C语言流程控制详解', knowledgePoint: '流程控制', content: '# C语言流程控制详解\n\n## 一、程序执行结构概述\n\nC语言程序有三种基本执行结构：\n\n1. **顺序结构**：代码从上到下逐行执行（默认）\n2. **选择结构**：根据条件判断选择执行哪条分支\n3. **循环结构**：重复执行一段代码直到条件不满足\n\n## 二、顺序结构\n\n顺序结构是最基本的程序结构，代码按照书写顺序**从上到下、从左到右**依次执行，每条语句执行一次。\n\n```c\n#include <stdio.h>\n\nint main() {\n    int a = 10;           // 第1步：声明并初始化变量\n    int b = 20;           // 第2步：声明并初始化变量\n    int sum = a + b;      // 第3步：计算两数之和\n    printf("sum = %d\\n", sum);  // 第4步：输出结果\n    return 0;             // 第5步：程序结束\n}\n```\n\n执行顺序：`a = 10` → `b = 20` → `sum = a + b` → `printf(...)` → `return 0`\n\n## 三、选择结构\n\n### 1. if 语句\n\n#### 单分支 if\n\n```c\nif (条件表达式) {\n    // 条件为真时执行的代码\n}\n```\n\n```c\nint score = 85;\nif (score >= 60) {\n    printf("恭喜你，及格了！\\n");\n}\n```\n\n#### 双分支 if-else\n\n```c\nif (条件表达式) {\n    // 条件为真时执行\n} else {\n    // 条件为假时执行\n}\n```\n\n```c\nint score = 55;\nif (score >= 60) {\n    printf("及格\\n");\n} else {\n    printf("不及格，继续努力！\\n");\n}\n```\n\n#### 多分支 if-else if-else\n\n```c\nif (条件1) {\n    // 条件1为真时执行\n} else if (条件2) {\n    // 条件1为假、条件2为真时执行\n} else if (条件3) {\n    // 条件1和2为假、条件3为真时执行\n} else {\n    // 所有条件都不满足时执行\n}\n```\n\n```c\nint score = 78;\nif (score >= 90) {\n    printf("优秀\\n");\n} else if (score >= 80) {\n    printf("良好\\n");\n} else if (score >= 70) {\n    printf("中等\\n");\n} else if (score >= 60) {\n    printf("及格\\n");\n} else {\n    printf("不及格\\n");\n}\n// 输出：中等\n```\n\n#### 嵌套 if\n\n```c\nint age = 25;\nint hasLicense = 1;  // 1表示有驾照，0表示没有\n\nif (age >= 18) {\n    if (hasLicense) {\n        printf("可以开车\\n");\n    } else {\n        printf("年龄够了，但需要先考驾照\\n");\n    }\n} else {\n    printf("年龄不足，还不能开车\\n");\n}\n```\n\n### 2. 条件运算符（三目运算符）\n\n```c\n变量 = 条件表达式 ? 值1 : 值2;\n```\n\n```c\nint a = 10, b = 20;\nint max = (a > b) ? a : b;   // 如果 a>b 则 max=a，否则 max=b\nprintf("最大值：%d\\n", max);  // 输出：20\n\n// 等价于：\nint max;\nif (a > b) {\n    max = a;\n} else {\n    max = b;\n}\n```\n\n> **使用建议**：三目运算符适合简单的二选一赋值，复杂的条件判断应使用 if-else。\n\n### 3. switch-case 语句\n\nswitch 语句用于**多路选择**，当判断条件是基于同一个变量（或表达式）的多个离散值时，比 if-else if 更清晰：\n\n```c\nswitch (表达式) {\n    case 常量1:\n        // 代码块1\n        break;\n    case 常量2:\n        // 代码块2\n        break;\n    case 常量3:\n        // 代码块3\n        break;\n    default:\n        // 默认代码块\n        break;\n}\n```\n\n```c\nint day = 3;\nswitch (day) {\n    case 1:\n        printf("星期一\\n");\n        break;\n    case 2:\n        printf("星期二\\n");\n        break;\n    case 3:\n        printf("星期三\\n");\n        break;\n    case 4:\n        printf("星期四\\n");\n        break;\n    case 5:\n        printf("星期五\\n");\n        break;\n    case 6:\n        printf("星期六\\n");\n        break;\n    case 7:\n        printf("星期日\\n");\n        break;\n    default:\n        printf("无效的日期\\n");\n        break;\n}\n// 输出：星期三\n```\n\n#### switch 的 fall-through 特性\n\n如果没有 `break`，程序会继续执行下一个 case 的代码（称为"穿透"）：\n\n```c\nint month = 3;\nswitch (month) {\n    case 1:\n    case 3:\n    case 5:\n    case 7:\n    case 8:\n    case 10:\n    case 12:\n        printf("31天\\n");\n        break;\n    case 4:\n    case 6:\n    case 9:\n    case 11:\n        printf("30天\\n");\n        break;\n    case 2:\n        printf("28或29天\\n");\n        break;\n    default:\n        printf("无效月份\\n");\n}\n// 输出：31天\n```\n\n> **switch 注意事项**：\n> - 表达式必须是**整型**（int、char、enum），不能是浮点型或字符串\n> - case 后面必须是**常量表达式**，不能是变量\n> - 每个 case 分支通常以 `break` 结尾，除非有意利用穿透\n> - `default` 是可选的，但建议始终写上以处理意外情况\n\n### 4. if-else 与 switch 选择指南\n\n| 场景 | 推荐方式 | 原因 |\n|------|----------|------|\n| 判断范围（如 60-70） | if-else | switch 不支持范围判断 |\n| 判断多个离散值 | switch | 代码更清晰、效率更高 |\n| 判断条件复杂（如 &&） | if-else | switch 只能判断等值 |\n| 分支数 ≤ 3 | if-else | 简单场景用 if-else |\n| 分支数 ≥ 4 | switch | 可读性更好 |\n\n## 四、循环结构\n\n### 1. for 循环\n\nfor 循环适合**已知循环次数**的场景：\n\n```c\nfor (初始化; 条件; 更新) {\n    // 循环体\n}\n```\n\n执行顺序：\n1. 执行**初始化**（只执行一次）\n2. 判断**条件**：为真则执行循环体，为假则退出循环\n3. 执行循环体\n4. 执行**更新**\n5. 回到步骤 2\n\n```c\n// 打印 1 到 10\nfor (int i = 1; i <= 10; i++) {\n    printf("%d ", i);\n}\nprintf("\\n");\n// 输出：1 2 3 4 5 6 7 8 9 10\n```\n\n#### for 循环的灵活用法\n\n```c\n// 多个变量同时控制\nfor (int i = 0, j = 10; i < j; i++, j--) {\n    printf("i=%d, j=%d\\n", i, j);\n}\n\n// 省略初始化（变量在外部已初始化）\nint i = 0;\nfor (; i < 10; i++) {\n    printf("%d ", i);\n}\n\n// 省略更新（在循环体内更新）\nfor (int i = 0; i < 10; ) {\n    printf("%d ", i);\n    i++;\n}\n\n// 无限循环\nfor (;;) {\n    // 需要 break 退出\n    break;\n}\n```\n\n### 2. while 循环\n\nwhile 循环适合**先判断后执行**的场景，如果条件一开始就不满足，循环体一次都不执行：\n\n```c\nwhile (条件) {\n    // 循环体\n}\n```\n\n```c\n// 计算 1 到 100 的和\nint sum = 0;\nint i = 1;\nwhile (i <= 100) {\n    sum += i;\n    i++;\n}\nprintf("1+2+...+100 = %d\\n", sum);  // 输出：5050\n```\n\n#### while 的常见用法\n\n```c\n// 读取输入直到用户输入特定值\nint num;\nprintf("请输入数字（输入0结束）：");\nscanf("%d", &num);\nwhile (num != 0) {\n    printf("你输入了：%d\\n", num);\n    printf("请输入数字（输入0结束）：");\n    scanf("%d", &num);\n}\nprintf("程序结束\\n");\n\n// 逐字符读取直到文件末尾\nchar ch;\nwhile ((ch = getchar()) != \'\\n\') {\n    putchar(ch);\n}\n```\n\n### 3. do-while 循环\n\ndo-while 循环**先执行后判断**，无论条件真假，循环体至少执行一次：\n\n```c\ndo {\n    // 循环体\n} while (条件);  // 注意：这里有分号！\n```\n\n```c\n// 至少执行一次的用户输入验证\nint password;\ndo {\n    printf("请输入密码（123456）：");\n    scanf("%d", &password);\n    if (password != 123456) {\n        printf("密码错误！\\n");\n    }\n} while (password != 123456);\nprintf("登录成功！\\n");\n```\n\n### 4. 三种循环对比\n\n| 特性 | for | while | do-while |\n|------|-----|-------|----------|\n| 最少执行次数 | 0次 | 0次 | 1次 |\n| 适用场景 | 已知循环次数 | 未知循环次数 | 至少执行一次 |\n| 循环变量 | 可在初始化中声明 | 必须在外部声明 | 必须在外部声明 |\n| 典型应用 | 遍历数组 | 读取文件 | 菜单系统 |\n\n## 五、跳转语句\n\n### 1. break\n\n`break` 用于**立即跳出**当前循环或 switch 语句：\n\n```c\n// 在循环中查找目标值\nint arr[] = {10, 25, 30, 45, 50, 65};\nint target = 45;\nint found = 0;\n\nfor (int i = 0; i < 6; i++) {\n    if (arr[i] == target) {\n        printf("找到了！位置：%d\\n", i);\n        found = 1;\n        break;  // 找到后立即退出循环\n    }\n}\nif (!found) {\n    printf("未找到\\n");\n}\n```\n\n> **注意**：`break` 只能跳出**最内层**的循环，无法跳出外层嵌套循环。\n\n### 2. continue\n\n`continue` 用于**跳过本次循环**中剩余的代码，直接进入下一次迭代：\n\n```c\n// 只打印奇数\nfor (int i = 1; i <= 10; i++) {\n    if (i % 2 == 0) {\n        continue;  // 跳过偶数，不执行后面的 printf\n    }\n    printf("%d ", i);\n}\n// 输出：1 3 5 7 9\n```\n\n**break 与 continue 对比**：\n\n```c\n// break：终止整个循环\nfor (int i = 1; i <= 5; i++) {\n    if (i == 3) break;\n    printf("%d ", i);\n}\n// 输出：1 2\n\n// continue：跳过本次迭代\nfor (int i = 1; i <= 5; i++) {\n    if (i == 3) continue;\n    printf("%d ", i);\n}\n// 输出：1 2 4 5\n```\n\n### 3. goto 语句\n\ngoto 允许**无条件跳转**到代码中的标记位置。虽然现代编程中 `goto` 的使用备受争议，但在某些场景（如错误处理、跳出多层嵌套循环）中仍有合理用途：\n\n```c\n#include <stdio.h>\n\nint main() {\n    int i, j;\n\n    for (i = 0; i < 5; i++) {\n        for (j = 0; j < 5; j++) {\n            if (i * j > 10) {\n                goto end;  // 跳出所有循环\n            }\n            printf("(%d,%d) ", i, j);\n        }\n        printf("\\n");\n    }\n\nend:\n    printf("\\n循环已退出\\n");\n    return 0;\n}\n```\n\n> **goto 使用原则**：\n> - 只能跳转到**同一函数内**的标签\n> - 不要用 goto 代替正常的循环和分支\n> - 主要用于错误清理和跳出深层嵌套\n\n## 六、嵌套与综合案例\n\n### 案例1：九九乘法表\n\n```c\n#include <stdio.h>\n\nint main() {\n    for (int i = 1; i <= 9; i++) {\n        for (int j = 1; j <= i; j++) {\n            printf("%d×%d=%-4d", j, i, i * j);\n        }\n        printf("\\n");\n    }\n    return 0;\n}\n```\n\n### 案例2：判断素数\n\n```c\n#include <stdio.h>\n#include <math.h>\n\nint main() {\n    int n, isPrime = 1;\n    printf("请输入一个整数：");\n    scanf("%d", &n);\n\n    if (n <= 1) {\n        isPrime = 0;\n    } else {\n        for (int i = 2; i <= sqrt(n); i++) {\n            if (n % i == 0) {\n                isPrime = 0;\n                break;\n            }\n        }\n    }\n\n    if (isPrime) {\n        printf("%d 是素数\\n", n);\n    } else {\n        printf("%d 不是素数\\n", n);\n    }\n    return 0;\n}\n```\n\n### 案例3：简单计算器（带菜单）\n\n```c\n#include <stdio.h>\n\nint main() {\n    int choice;\n    double a, b;\n\n    do {\n        printf("\\n=== 简单计算器 ===\\n");\n        printf("1. 加法\\n");\n        printf("2. 减法\\n");\n        printf("3. 乘法\\n");\n        printf("4. 除法\\n");\n        printf("0. 退出\\n");\n        printf("请选择：");\n        scanf("%d", &choice);\n\n        if (choice >= 1 && choice <= 4) {\n            printf("请输入两个数：");\n            scanf("%lf %lf", &a, &b);\n        }\n\n        switch (choice) {\n            case 1: printf("%.2f + %.2f = %.2f\\n", a, b, a + b); break;\n            case 2: printf("%.2f - %.2f = %.2f\\n", a, b, a - b); break;\n            case 3: printf("%.2f × %.2f = %.2f\\n", a, b, a * b); break;\n            case 4:\n                if (b != 0) {\n                    printf("%.2f ÷ %.2f = %.2f\\n", a, b, a / b);\n                } else {\n                    printf("错误：除数不能为零！\\n");\n                }\n                break;\n            case 0: printf("感谢使用，再见！\\n"); break;\n            default: printf("无效选择，请重新输入！\\n\");\n        }\n    } while (choice != 0);\n\n    return 0;\n}\n```\n\n## 七、常见错误\n\n| 序号 | 错误类型 | 错误代码 | 正确代码 | 说明 |\n|------|----------|----------|----------|------|\n| 1 | if 条件赋值 | `if (a = 5)` | `if (a == 5)` | `=` 是赋值，`==` 是判断 |\n| 2 | switch 漏 break | `case 1: ... case 2: ...` | `case 1: ...; break;` | 导致 fall-through 意外执行 |\n| 3 | 循环条件写错 | `for (int i=0; i<10; i--)` | `for (int i=0; i<10; i++)` | 死循环或无法进入循环 |\n| 4 | do-while 漏分号 | `while (i<10)` | `while (i<10);` | do-while 末尾必须有分号 |\n| 5 | 循环体多条语句未加花括号 | `for(...) a++; b++;` | `for(...) { a++; b++; }` | 只有 a++ 在循环中 |\n| 6 | 无限循环无退出 | `while(1) { }` | 添加 `break` 条件 | 导致程序卡死 |\n| 7 | if-else 匹配错误 | 嵌套 if 缺花括号 | 始终使用花括号 | else 匹配最近的 if |\n| 8 | 浮点数等值判断 | `if (f == 0.1)` | `if (fabs(f-0.1) < 1e-6)` | 浮点数有精度误差 |\n\n## 八、练习建议\n\n### 基础题（必做）\n\n1. **成绩等级判断**：输入分数，输出等级（优秀/良好/中等/及格/不及格）\n2. **闰年判断**：输入年份，判断是否为闰年\n3. **1-100求和**：分别用 for、while、do-while 三种循环实现\n4. **九九乘法表**：打印完整的九九乘法表\n5. **计算阶乘**：输入 n，计算 n! = 1×2×...×n\n\n### 进阶题（选做）\n\n6. **打印菱形**：用户输入行数 n，用 * 号打印菱形图案\n7. **猜数字游戏**：生成 1-100 的随机数，让用户猜（提示大小），直到猜对\n8. **水仙花数**：找出所有三位水仙花数（各位数字立方和等于原数）\n9. **最大公约数**：用辗转相除法求两个数的最大公约数\n\n### 挑战题\n\n10. **打印日历**：输入年份和月份，打印该月的日历\n11. **穷举法解百钱百鸡**：公鸡5元/只，母鸡3元/只，小鸡1元/3只，用100元买100只鸡，求所有方案', difficulty: 'easy', generatedBy: 'auto-pipeline', hallucinationScore: 0.95, createdAt: '2026-07-14T10:00:00' },
  { id: 212, type: 'code', title: '循环结构示例代码', knowledgePoint: '控制结构', content: '## 问题描述\n综合演示 C 语言三种循环结构（for / while / do-while）的标准写法：遍历打印 1~10、求 1~100 累加和、以及 do-while 的至少执行一次特性。\n\n## 输入格式\n无输入，程序运行后直接输出三段循环的结果。\n\n## 输出格式\n按顺序输出以下 3 段内容：\n1. `for循环：1 2 3 4 5 6 7 8 9 10 `\n2. `1-100的和：5050`\n3. `count = 5`\n\n## 样例输入 1\n```plaintext\n（无输入）\n```\n\n## 样例输出 1\n```plaintext\nfor循环：1 2 3 4 5 6 7 8 9 10 \n1-100的和：5050\ncount = 5\n```\n\n## 代码解析\n三种循环的适用场景：\n1. **for 循环**：适合**已知循环次数**的场景，结构为「初始化; 条件; 步长」三部分写在一处，便于遍历\n2. **while 循环**：适合**先判断后执行**的场景，条件若一开始为假则循环体一次都不会跑（用于 1~100 求和，循环次数由 i<=100 控制）\n3. **do-while 循环**：**先执行后判断**，无论条件真假至少执行一次循环体（count 从 0 开始，执行 5 次后 count=5 才退出）\n\n```c\n#include <stdio.h>\n\nint main() {\n    // for循环：打印1-10\n    printf(\"for循环：\");\n    for (int i = 1; i <= 10; i++) {\n        printf(\"%d \", i);\n    }\n    printf(\"\\n\");\n\n    // while循环：求1-100的和\n    int sum = 0, i = 1;\n    while (i <= 100) {\n        sum += i;\n        i++;\n    }\n    printf(\"1-100的和：%d\\n\", sum);\n\n    // do-while循环\n    int count = 0;\n    do {\n        count++;\n    } while (count < 5);\n    printf(\"count = %d\\n\", count);\n\n    return 0;\n}\n```\n\n## 改进建议\n- 求 1~N 累加和可改用等差数列公式 `N*(N+1)/2`，时间复杂度从 O(N) 降为 O(1)\n- do-while 末尾的分号 `;` 不能漏写，否则编译报语法错误\n- for 循环中若使用 C89 标准，int i 必须声明在循环外', difficulty: 'easy', generatedBy: 'auto-pipeline', hallucinationScore: 0.97, compileStatus: 'PASSED', createdAt: '2026-07-14T10:00:00' },
  { id: 213, type: 'exercise', title: '流程控制练习题', knowledgePoint: '控制结构', content: '# 流程控制练习\n\n1. 写一个程序判断闰年\n2. 用循环计算n的阶乘\n3. 打印九九乘法表', difficulty: 'medium', generatedBy: 'auto-pipeline', hallucinationScore: 0.95, createdAt: '2026-07-14T10:00:00' },
  { id: 215, type: 'course-video', title: 'C语言简史与概述', knowledgePoint: 'C语言概述', content: 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=1', difficulty: 'easy', generatedBy: 'course-video', createdAt: '2026-07-14T10:00:00' },

  // ===== 数组与指针章节资源 =====
  { id: 221, type: 'article', title: '指针与数组关系详解', knowledgePoint: '指针', content: '# 指针与数组\n\n## 数组名的本质\n数组名在大多数情况下代表数组首元素的地址。\n\n## 指针与数组的等价性\n```c\nint arr[5] = {1, 2, 3, 4, 5};\nint *p = arr;\narr[0] == *(p+0)  // 等价\narr[1] == *(p+1)  // 等价\n```\n\n## 指针算术\n指针加减运算的步长取决于指针类型。', difficulty: 'medium', generatedBy: 'auto-pipeline', hallucinationScore: 0.94, createdAt: '2026-07-14T10:00:00' },
  { id: 222, type: 'explanation', title: '动态内存管理深入解析', knowledgePoint: '动态内存', content: '# 动态内存管理\n\n## 内存分区\n- 栈区：存放局部变量\n- 堆区：动态分配的内存\n- 全局区：全局变量和静态变量\n\n## malloc与free\n```c\nint *p = (int*)malloc(n * sizeof(int));\nif (p != NULL) {\n    // 使用内存\n    free(p);\n    p = NULL;\n}\n```\n\n## 常见问题\n- 内存泄漏：malloc后未free\n- 野指针：free后未置NULL\n- 重复释放：同一内存free两次', difficulty: 'hard', generatedBy: 'auto-pipeline', hallucinationScore: 0.93, createdAt: '2026-07-14T10:00:00' },
  { id: 223, type: 'code', title: '指针基础示例', knowledgePoint: '指针', content: '## 问题描述\n演示指针的基本概念与核心操作：取地址 `&`、解引用 `*`、通过指针间接修改原变量的值，帮助理解「指针保存的是地址」这一本质。\n\n## 输入格式\n无输入，程序运行后直接输出内置变量 a 与指针 p 的相关信息与修改后的值。\n\n## 输出格式\n依次输出以下 6 行：\n1. `a的值：10`（a 的初值）\n2. `a的地址：0x...`（a 在内存中的地址，用 %p 打印）\n3. `p的值（a的地址）：0x...`（p 存储的地址，与第 2 行相等）\n4. `p指向的值：10`（*p 即 a 的值）\n5. `修改后a的值：20`（通过 *p=20 间接修改 a 后输出）\n\n## 样例输入 1\n```plaintext\n（无输入）\n```\n\n## 样例输出 1\n```plaintext\na的值：10\na的地址：0x7ffd...\np的值（a的地址）：0x7ffd...\np指向的值：10\n修改后a的值：20\n```\n\n## 代码解析\n指针入门最核心的 3 个运算符：\n1. **`&a` 取地址**：得到变量 a 在内存中的起始地址，赋值给指针变量 `int *p = &a`，此时称「p 指向 a」\n2. **`*p` 解引用**：通过 p 里存的地址，反向访问到 a 的值（读操作），`*p` 等价于 `a`\n3. **`*p = 20` 写操作**：通过指针修改它指向的内存，这一步结束后 a 的值也变为 20，是指针最典型的「间接修改」用法\n\n```c\n#include <stdio.h>\n\nint main() {\n    int a = 10;\n    int *p = &a;  // p指向a\n\n    printf(\"a的值：%d\\n\", a);\n    printf(\"a的地址：%p\\n\", &a);\n    printf(\"p的值（a的地址）：%p\\n\", p);\n    printf(\"p指向的值：%d\\n\", *p);\n\n    *p = 20;  // 通过指针修改a的值\n    printf(\"修改后a的值：%d\\n\", a);\n\n    return 0;\n}\n```\n\n## 改进建议\n- 使用 `%p` 打印地址时，为了可移植性最好强制转换为 `void*`：`printf(\"%p\", (void*)&a)`\n- 永远不要解引用未初始化的指针：`int *q; *q = 10;` 是典型的野指针写入，会导致段错误或内存污染', difficulty: 'medium', generatedBy: 'auto-pipeline', hallucinationScore: 0.96, compileStatus: 'PASSED', createdAt: '2026-07-14T10:00:00' },
  { id: 224, type: 'code', title: '动态内存分配示例', knowledgePoint: '动态内存', content: '## 问题描述\n演示 C 语言堆内存的标准管理流程：使用 `malloc` 申请一段长度为 n 的 int 数组、检查返回值是否为 NULL、赋值后遍历打印、最后用 `free` 释放并将指针置 NULL，避免内存泄漏与野指针。\n\n## 输入格式\n无输入，程序内置 `n = 5`，直接运行即可。\n\n## 输出格式\n依次输出 5 行，每行格式：`arr[i] = (i+1)*10`，即：\n```\narr[0] = 10\narr[1] = 20\narr[2] = 30\narr[3] = 40\narr[4] = 50\n```\n若申请失败则输出 `内存分配失败` 并返回退出码 1。\n\n## 样例输入 1\n```plaintext\n（无输入）\n```\n\n## 样例输出 1\n```plaintext\narr[0] = 10\narr[1] = 20\narr[2] = 30\narr[3] = 40\narr[4] = 50\n```\n\n## 代码解析\n动态内存操作的**安全四部曲**：\n1. **申请**：`malloc(n * sizeof(int))` 按字节数分配，使用 `sizeof` 而非硬编码 4 字节，保证跨平台（不同机器 int 大小可能不同）\n2. **判空**：`if (arr == NULL)` 必须检查！内存不足时 malloc 返回 NULL，继续使用会段错误\n3. **使用**：下标访问 `arr[i]` 等价于 `*(arr+i)`，因为 malloc 返回的地址按基本类型对齐\n4. **释放**：`free(arr)` 必须与 malloc 一一对应，释放后立即 `arr = NULL` 防止二次 free 或野指针\n\n```c\n#include <stdio.h>\n#include <stdlib.h>\n\nint main() {\n    int n = 5;\n    int *arr = (int*)malloc(n * sizeof(int));\n\n    if (arr == NULL) {\n        printf(\"内存分配失败\\n\");\n        return 1;\n    }\n\n    for (int i = 0; i < n; i++) {\n        arr[i] = (i + 1) * 10;\n    }\n\n    for (int i = 0; i < n; i++) {\n        printf(\"arr[%d] = %d\\n\", i, arr[i]);\n    }\n\n    free(arr);\n    arr = NULL;\n    return 0;\n}\n```\n\n## 改进建议\n- C 语言中 `malloc` 的返回值通常**不需要强制转换为 `(int*)`**，`void*` 可自动提升为任意指针类型；强制转换主要为兼容 C++ 编译器\n- 当 n 较大且元素个数可能溢出时，改用 `calloc(n, sizeof(int))` 可同时清零，或手动 `memset` 避免使用未初始化内存\n- 生产代码建议使用 valgrind 或 AddressSanitizer 检查是否存在泄漏/越界', difficulty: 'medium', generatedBy: 'auto-pipeline', hallucinationScore: 0.95, compileStatus: 'PASSED', createdAt: '2026-07-14T10:00:00' },
  { id: 225, type: 'exercise', title: '指针专项练习', knowledgePoint: '指针', content: '# 指针练习题\n\n1. 指针变量存储的是什么？\n2. 如何通过指针交换两个变量的值？\n3. 什么是野指针？如何避免？\n4. malloc后为什么要检查返回值？', difficulty: 'medium', generatedBy: 'auto-pipeline', hallucinationScore: 0.94, createdAt: '2026-07-14T10:00:00' },
  { id: 227, type: 'course-video', title: '第一个C程序：Hello World', knowledgePoint: 'C语言概述', content: 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=7', difficulty: 'easy', generatedBy: 'course-video', createdAt: '2026-07-14T10:00:00' },
  { id: 228, type: 'mindmap', title: '指针知识图谱', knowledgePoint: '指针', content: '# 指针知识图谱\n## 基本概念\n### 指针变量\n- 存储内存地址的变量\n- 声明：int *p\n- 指针类型决定步长\n### 取地址运算符&\n- &a 获取a的地址\n- scanf中必须使用&\n- 数组名本身就是地址\n### 解引用运算符*\n- *p 获取p指向的值\n- *与&互为逆运算\n- *p = 10 可修改指向的值\n## 指针与数组\n### 数组名含义\n- 数组名是首元素地址\n- sizeof(数组名)例外：得到整个数组大小\n- 数组名不可自增（非左值）\n### 指针算术\n- p+1 偏移sizeof(*p)字节\n- 指针差值得到元素间隔\n- 指针比较：同一数组内才有意义\n### 数组遍历\n- 下标法：arr[i]\n- 指针法：*(p+i)\n- 指针自增法：*p++\n## 指针与函数\n### 指针参数\n- 值传递与地址传递\n- 通过指针修改实参\n- swap(&a, &b)经典示例\n### 返回指针\n- 不要返回局部变量地址\n- 可返回静态/堆变量地址\n- 函数指针：int (*fp)(int,int)\n### 回调机制\n- qsort的比较函数\n- 函数指针数组\n- 回调函数的实际应用\n## 多级指针\n### 二级指针\n- int **pp 指向指针的指针\n- 修改指针本身需要传二级指针\n- 链表操作中的常见用法\n### 指针数组与数组指针\n- int *arr[10] 指针数组\n- int (*arr)[10] 数组指针\n- 注意[]优先级高于*\n## 常见陷阱\n### 野指针\n- 未初始化的指针\n- free后未置NULL\n- 访问已释放的内存\n### 空指针\n- NULL的定义与使用\n- 解引用NULL导致段错误\n- 使用前检查if(p!=NULL)\n### 内存泄漏\n- malloc后未free\n- 循环中重复malloc\n- 使用valgrind检测泄漏\n### 越界访问\n- 指针越出数组边界\n- 缓冲区溢出风险\n- off-by-one错误', difficulty: 'hard', generatedBy: 'auto-pipeline', hallucinationScore: 0.93, createdAt: '2026-07-14T10:00:00' },

  // ===== 函数章节资源 =====
  { id: 231, type: 'article', title: 'C语言函数详解', knowledgePoint: '函数', content: '# C语言函数详解\n\n## 一、函数的基本概念\n\n函数是C语言程序的基本组成单元，用于将代码**模块化**，实现代码复用和逻辑封装。\n\n**使用函数的好处**：\n- **代码复用**：避免重复编写相同逻辑\n- **模块化设计**：将复杂问题分解为多个小问题\n- **易于维护**：修改一处，所有调用处都生效\n- **提高可读性**：函数名本身就是对代码的注释\n\n## 二、函数定义与声明\n\n### 函数定义语法\n\n```c\n返回类型 函数名(形参类型 形参名, ...) {\n    // 函数体：具体执行的代码\n    return 返回值;  // 如果返回类型不是 void 则必须返回\n}\n```\n\n### 完整示例\n\n```c\n#include <stdio.h>\n\n// 函数声明（原型声明）—— 告诉编译器此函数的存在\nint add(int a, int b);\nfloat getAverage(int arr[], int n);\nvoid printLine(int length);\n\nint main() {\n    // 函数调用\n    int sum = add(3, 5);\n    printf("3 + 5 = %d\\n", sum);\n\n    int scores[] = {85, 92, 78, 90, 88};\n    float avg = getAverage(scores, 5);\n    printf("平均分：%.2f\\n", avg);\n\n    printLine(30);\n    return 0;\n}\n\n// 函数定义\nint add(int a, int b) {\n    return a + b;\n}\n\nfloat getAverage(int arr[], int n) {\n    int sum = 0;\n    for (int i = 0; i < n; i++) {\n        sum += arr[i];\n    }\n    return (float)sum / n;\n}\n\nvoid printLine(int length) {\n    for (int i = 0; i < length; i++) {\n        printf("-");\n    }\n    printf("\\n");\n}\n```\n\n### 函数声明与定义的区别\n\n| 概念 | 位置 | 作用 | 示例 |\n|------|------|------|------|\n| 函数声明（原型） | 调用之前 | 告诉编译器函数的存在 | `int add(int, int);` |\n| 函数定义 | 任意位置 | 提供函数的具体实现 | `int add(int a, int b) { return a+b; }` |\n\n> **关键规则**：函数必须先声明（或定义）再调用。如果函数定义在调用之后，必须在调用前写函数声明。\n\n## 三、参数传递\n\n### 1. 值传递（Pass by Value）\n\n值传递时，**形参是实参的副本**，在函数内部修改形参**不会影响**实参：\n\n```c\n#include <stdio.h>\n\n// 值传递：无法交换实参\nvoid swapValue(int a, int b) {\n    printf("swapValue 内部：交换前 a=%d, b=%d\\n", a, b);\n    int temp = a;\n    a = b;\n    b = temp;\n    printf("swapValue 内部：交换后 a=%d, b=%d\\n", a, b);\n}\n\nint main() {\n    int x = 10, y = 20;\n    printf("main 调用前：x=%d, y=%d\\n", x, y);\n\n    swapValue(x, y);\n\n    printf("main 调用后：x=%d, y=%d\\n\", x, y);\n    // 输出：x=10, y=20 —— 值没有改变！\n    return 0;\n}\n```\n\n### 2. 地址传递（Pass by Pointer）\n\n通过传递**指针**（地址），函数可以直接修改实参的值：\n\n```c\n#include <stdio.h>\n\n// 地址传递：真正交换实参\nvoid swapPointer(int *a, int *b) {\n    printf("swapPointer 内部：交换前 *a=%d, *b=%d\\n", *a, *b);\n    int temp = *a;\n    *a = *b;\n    *b = temp;\n    printf("swapPointer 内部：交换后 *a=%d, *b=%d\\n", *a, *b);\n}\n\nint main() {\n    int x = 10, y = 20;\n    printf("main 调用前：x=%d, y=%d\\n", x, y);\n\n    swapPointer(&x, &y);  // 传递地址\n\n    printf("main 调用后：x=%d, y=%d\\n\", x, y);\n    // 输出：x=20, y=10 —— 值成功交换！\n    return 0;\n}\n```\n\n### 3. 数组作为函数参数\n\n数组名作为参数传递时，实际上传递的是数组首元素的地址（退化为指针）：\n\n```c\n#include <stdio.h>\n\n// 数组参数：实际传递的是指针\nvoid modifyArray(int arr[], int n) {\n    for (int i = 0; i < n; i++) {\n        arr[i] *= 2;  // 直接修改原数组\n    }\n}\n\n// 等价写法：使用指针\nvoid modifyArray2(int *arr, int n) {\n    for (int i = 0; i < n; i++) {\n        *(arr + i) *= 2;\n    }\n}\n\nint main() {\n    int nums[] = {1, 2, 3, 4, 5};\n    int n = 5;\n\n    printf("修改前：\");\n    for (int i = 0; i < n; i++) printf("%d ", nums[i]);\n    printf(\"\\n\");\n\n    modifyArray(nums, n);\n\n    printf(\"修改后：\");\n    for (int i = 0; i < n; i++) printf(\"%d \", nums[i]);\n    printf(\"\\n\");\n    // 输出：2 4 6 8 10\n    return 0;\n}\n```\n\n### 传递方式对比\n\n| 特性 | 值传递 | 地址传递 | 数组传递 |\n|------|--------|----------|----------|\n| 是否修改实参 | 否 | 是 | 是 |\n| 传递内容 | 值的副本 | 变量地址 | 首元素地址 |\n| 内存开销 | 拷贝整个值 | 只传4/8字节地址 | 只传地址 |\n| 使用场景 | 只需读取数据 | 需要修改实参 | 操作数组 |\n\n## 四、返回值\n\n### return 语句\n\n```c\n// 返回基本类型\nint add(int a, int b) {\n    return a + b;  // 返回表达式的结果\n}\n\n// 返回指针\nint* getMax(int *a, int *b) {\n    return (*a > *b) ? a : b;  // 返回较大值的地址\n}\n\n// 返回结构体\nstruct Point createPoint(int x, int y) {\n    struct Point p = {x, y};\n    return p;\n}\n\n// void 函数：无返回值\nvoid printMessage() {\n    printf(\"Hello!\\n\");\n    // return;  // 可选的空返回\n}\n```\n\n### 返回值注意事项\n\n```c\n// ❌ 错误：返回局部变量的地址\nint* getLocalPointer() {\n    int local = 10;\n    return &local;  // 危险！函数返回后 local 已被销毁\n}\n\n// ✅ 正确：返回动态分配的内存地址\nint* getHeapPointer() {\n    int *p = (int*)malloc(sizeof(int));\n    *p = 10;\n    return p;  // 堆内存不会自动释放，调用者负责 free\n}\n\n// ✅ 正确：返回静态变量的地址\nint* getStaticPointer() {\n    static int value = 10;  // 静态变量生命周期贯穿整个程序\n    return &value;\n}\n```\n\n## 五、递归\n\n递归是函数**调用自身**的编程技巧，必须包含**终止条件**（递归基）避免无限递归。\n\n### 递归三要素\n\n1. **递归定义**：将问题分解为更小的同类子问题\n2. **终止条件**：最小子问题可以直接求解\n3. **递归调用**：问题规模逐步缩小，最终收敛到终止条件\n\n### 经典递归示例\n\n```c\n#include <stdio.h>\n\n// 例1：计算阶乘 n! = n × (n-1)!\nint factorial(int n) {\n    if (n <= 1) return 1;           // 终止条件\n    return n * factorial(n - 1);     // 递归调用\n}\n\n// 例2：计算斐波那契数列 fib(n) = fib(n-1) + fib(n-2)\nint fibonacci(int n) {\n    if (n == 0) return 0;            // 终止条件\n    if (n == 1) return 1;            // 终止条件\n    return fibonacci(n - 1) + fibonacci(n - 2);\n}\n\n// 例3：汉诺塔问题\nvoid hanoi(int n, char from, char to, char aux) {\n    if (n == 1) {\n        printf(\"将盘子1从 %c 移到 %c\\n\", from, to);\n        return;\n    }\n    hanoi(n - 1, from, aux, to);\n    printf(\"将盘子%d从 %c 移到 %c\\n\", n, from, to);\n    hanoi(n - 1, aux, to, from);\n}\n\nint main() {\n    printf(\"5! = %d\\n\", factorial(5));       // 输出：120\n    printf(\"fib(10) = %d\\n\", fibonacci(10));  // 输出：55\n\n    printf(\"\\n汉诺塔（3个盘子）：\\n\");\n    hanoi(3, \'A\', \'C\', \'B\');\n    return 0;\n}\n```\n\n### 递归的优缺点\n\n| 优点 | 缺点 |\n|------|------|\n| 代码简洁优雅 | 函数调用开销大 |\n| 自然表达分治思想 | 可能栈溢出 |\n| 适合树/图等递归结构 | 存在重复计算 |\n\n> **优化建议**：对于斐波那契等存在大量重复计算的递归，使用**记忆化**（缓存已计算结果）或**迭代**替代。\n\n## 六、变量作用域与生命周期\n\n### 作用域分类\n\n```c\n#include <stdio.h>\n\nint globalVar = 100;            // 全局变量：整个文件可见\n\nvoid test() {\n    int localVar = 50;          // 局部变量：仅在 test() 内可见\n    static int staticVar = 0;   // 静态局部变量：仅 test() 内可见，但生命周期为整个程序\n    staticVar++;\n    printf(\"staticVar = %d\\n\", staticVar);\n}\n\nint main() {\n    int localVar = 200;         // 与 test() 中的 localVar 互不影响\n    printf(\"main 中的 localVar = %d\\n\", localVar);\n    printf(\"globalVar = %d\\n\", globalVar);\n\n    test();  // 输出：staticVar = 1\n    test();  // 输出：staticVar = 2\n    test();  // 输出：staticVar = 3\n    return 0;\n}\n```\n\n### 变量类型对比\n\n| 类型 | 关键字 | 作用域 | 生命周期 | 初始值 |\n|------|--------|--------|----------|--------|\n| 局部变量 | 无 | 函数内 | 函数调用期间 | 不确定（随机值） |\n| 静态局部变量 | `static` | 函数内 | 整个程序运行期间 | 0 |\n| 全局变量 | 无 | 整个文件 | 整个程序运行期间 | 0 |\n| 静态全局变量 | `static` | 当前文件 | 整个程序运行期间 | 0 |\n\n## 七、函数指针入门\n\n函数指针是指向函数的指针，允许将函数作为参数传递，实现**回调机制**。\n\n### 函数指针声明\n\n```c\nint (*funcPtr)(int, int);  // 声明一个指向「返回 int、接收两个 int 参数」的函数的指针\n```\n\n### 函数指针完整示例\n\n```c\n#include <stdio.h>\n\n// 定义两个普通函数\nint add(int a, int b) { return a + b; }\nint multiply(int a, int b) { return a * b; }\n\n// 函数指针作为参数（回调）\nint calculate(int a, int b, int (*operation)(int, int)) {\n    return operation(a, b);\n}\n\nint main() {\n    // 函数指针赋值\n    int (*fp)(int, int);\n\n    fp = add;           // 指向 add 函数\n    printf(\"3 + 5 = %d\\n\", fp(3, 5));\n\n    fp = multiply;      // 指向 multiply 函数\n    printf(\"3 × 5 = %d\\n\", fp(3, 5));\n\n    // 回调用法\n    printf(\"计算：%d\\n\", calculate(10, 5, add));       // 15\n    printf(\"计算：%d\\n\", calculate(10, 5, multiply));   // 50\n\n    return 0;\n}\n```\n\n### 函数指针数组\n\n```c\n#include <stdio.h>\n\nint add(int a, int b) { return a + b; }\nint sub(int a, int b) { return a - b; }\nint mul(int a, int b) { return a * b; }\nint div_op(int a, int b) { return b != 0 ? a / b : 0; }\n\nint main() {\n    // 函数指针数组\n    int (*operations[4])(int, int) = {add, sub, mul, div_op};\n    char *names[] = {\"加法\", \"减法\", \"乘法\", \"除法\"};\n\n    int x = 20, y = 4;\n    for (int i = 0; i < 4; i++) {\n        printf(\"%d %s %d = %d\\n\", x, names[i], y, operations[i](x, y));\n    }\n    return 0;\n}\n```\n\n## 八、常见错误\n\n| 序号 | 错误类型 | 错误代码 | 正确代码 | 说明 |\n|------|----------|----------|----------|------|\n| 1 | 忘记函数声明 | 调用时未声明 | 在调用前加函数原型 | 编译器报 implicit declaration |\n| 2 | 值传递误解 | swap 用值传递 | 改用指针传递 | 形参修改不影响实参 |\n| 3 | 返回局部变量地址 | `return &local;` | 返回动态分配或静态变量 | 局部变量已销毁 |\n| 4 | 递归无终止条件 | 无限递归 | 必须有终止条件 | 导致栈溢出 |\n| 5 | 数组参数 sizeof | 函数内 `sizeof(arr)` | 额外传数组长度 | 退化为指针，大小为8 |\n| 6 | 忘记 return | 非 void 函数无返回值 | 确保所有路径都有 return | 未定义行为 |\n| 7 | 函数指针声明 | `int *fp(int,int)` | `int (*fp)(int,int)` | 括号位置决定含义 |\n| 8 | 参数类型不匹配 | 传 int 实参给 float 形参 | 明确类型转换 | 可能导致精度丢失 |\n\n## 九、练习建议\n\n### 基础题（必做）\n\n1. **最大值函数**：编写函数 `max(int a, int b)` 返回最大值\n2. **素数判断**：编写函数 `isPrime(int n)` 判断 n 是否为素数\n3. **阶乘函数**：分别用迭代和递归两种方式实现阶乘\n4. **数组求和**：编写函数 `sumArray(int arr[], int n)` 返回数组元素之和\n5. **交换函数**：用指针编写 `swap(int *a, int *b)` 交换两个变量的值\n\n### 进阶题（选做）\n\n6. **字符串长度**：不用 `strlen`，自己实现 `myStrlen(char *s)`\n7. **汉诺塔**：用递归实现汉诺塔问题，输出移动步骤\n8. **冒泡排序**：编写函数 `bubbleSort(int arr[], int n)` 对数组排序\n9. **计算器**：用函数指针数组实现支持加减乘除的计算器\n\n### 挑战题\n\n10. **二分查找**：编写函数 `binarySearch(int arr[], int n, int target)` 实现二分查找\n11. **快速排序**：用递归实现快速排序算法', difficulty: 'medium', generatedBy: 'auto-pipeline', hallucinationScore: 0.95, createdAt: '2026-07-14T10:00:00' },
  { id: 232, type: 'explanation', title: '函数指针与回调机制详解', knowledgePoint: '函数', content: '# 函数指针与回调\n\n## 函数指针声明\n```c\nint (*funcPtr)(int, int);\n```\n\n## 回调函数示例\n```c\nvoid process(int *arr, int n, int (*op)(int)) {\n    for (int i = 0; i < n; i++)\n        arr[i] = op(arr[i]);\n}\n```\n\n## 实际应用\n- qsort排序函数\n- 事件处理机制\n- 策略模式', difficulty: 'hard', generatedBy: 'auto-pipeline', hallucinationScore: 0.92, createdAt: '2026-07-14T10:00:00' },
  { id: 233, type: 'code', title: '函数基础示例', knowledgePoint: '函数', content: '## 问题描述\n演示 C 语言函数的三大核心要素：**函数声明（原型）**、**函数定义**、**函数调用**，实现一个加法函数 `add` 与一个分隔线打印函数 `printLine`，完整展现「先声明→再使用→后定义」的组织方式。\n\n## 输入格式\n无输入，程序内置参数 `a=3、b=5`，直接运行即可。\n\n## 输出格式\n依次输出 2 行：\n1. `3 + 5 = 8`\n2. 一行 20 个 `-` 组成的分隔线（后面跟换行）\n\n## 样例输入 1\n```plaintext\n（无输入）\n```\n\n## 样例输出 1\n```plaintext\n3 + 5 = 8\n--------------------\n```\n\n## 代码解析\n函数结构的四要素：\n1. **返回类型**：`add` 返回 `int`（两数之和），`printLine` 返回 `void`（无返回值）\n2. **函数名**：建议见名知义，使用动词开头（add、printLine）\n3. **参数列表**：形参是实参的副本（值传递），修改形参 a、b 不会影响调用处的实参\n4. **return**：`add` 末尾 `return a+b` 将结果带回调用点；`void` 函数可用空 `return;` 或直接省略\n\n注意：`main` 函数在最上方调用 `add/printLine` 时，编译器还没看到它们的定义，因此必须在文件开头写上**函数声明（原型）**，否则编译报错「implicit declaration of function」。\n\n```c\n#include <stdio.h>\n\n// 函数声明\nint add(int a, int b);\nvoid printLine(int n);\n\nint main() {\n    int result = add(3, 5);\n    printf(\"3 + 5 = %d\\n\", result);\n\n    printLine(20);\n    return 0;\n}\n\n// 函数定义\nint add(int a, int b) {\n    return a + b;\n}\n\nvoid printLine(int n) {\n    for (int i = 0; i < n; i++) {\n        printf(\"-\");\n    }\n    printf(\"\\n\");\n}\n```\n\n## 改进建议\n- 在大型项目中建议把函数声明放到 `.h` 头文件，定义放到 `.c` 源文件，通过 include 复用到多个编译单元\n- `printLine` 中的分隔符字符、字符个数都可抽成参数或宏定义（如 `#define SEP_CHAR \'-\'`），提升复用性\n- 需要在函数内部「修改实参」时要传指针（地址传递），不要依赖值传递', difficulty: 'easy', generatedBy: 'auto-pipeline', hallucinationScore: 0.97, compileStatus: 'PASSED', createdAt: '2026-07-14T10:00:00' },
  { id: 234, type: 'exercise', title: '函数专项练习', knowledgePoint: '函数', content: '# 函数练习题\n\n1. 写一个函数计算两个数的最大值\n2. 写一个递归函数计算阶乘\n3. 什么是函数指针？如何使用？', difficulty: 'medium', generatedBy: 'auto-pipeline', hallucinationScore: 0.94, createdAt: '2026-07-14T10:00:00' },
  { id: 236, type: 'course-video', title: '7.1 函数定义与调用', knowledgePoint: '函数', content: 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=71', difficulty: 'medium', generatedBy: 'course-video', createdAt: '2026-07-14T10:00:00' },

  // ===== PPT课件资源（照片形式） =====
  { id: 261, type: 'course-ppt', title: 'C语言基础语法学习课件', knowledgePoint: '基础语法', content: '[PPT_FILE:基础语法_课件_讯飞v2.pptx]', coverImage: '/api/ppt/slides/基础语法_课件_讯飞v2.pptx/slide-1.png', difficulty: 'easy', generatedBy: 'ppt-agent', hallucinationScore: 0.96, createdAt: '2026-07-18T18:08:00' },
  { id: 262, type: 'course-ppt', title: 'C语言基础语法学习课件', knowledgePoint: '基础语法', content: '[PPT_FILE:基础语法_课件_讯飞.pptx]', coverImage: '/api/ppt/slides/基础语法_课件_讯飞.pptx/slide-1.png', difficulty: 'easy', generatedBy: 'ppt-agent', hallucinationScore: 0.96, createdAt: '2026-07-18T18:09:00' },
  { id: 263, type: 'course-ppt', title: 'C语言基础语法学习课件', knowledgePoint: '基础语法', content: '[PPT_FILE:基础语法_课件.pptx]', coverImage: '/api/ppt/slides/基础语法_课件.pptx/slide-1.png', difficulty: 'easy', generatedBy: 'ppt-agent', hallucinationScore: 0.96, createdAt: '2026-07-18T18:10:00' },
  { id: 264, type: 'course-ppt', title: 'C语言流程控制学习课件', knowledgePoint: '流程控制', content: '[PPT_FILE:流程控制_课件_讯飞.pptx]', coverImage: '/api/ppt/slides/流程控制_课件_讯飞.pptx/slide-1.png', difficulty: 'medium', generatedBy: 'ppt-agent', hallucinationScore: 0.96, createdAt: '2026-07-18T18:11:00' },
  { id: 265, type: 'course-ppt', title: 'C语言数组与指针学习课件', knowledgePoint: '数组与指针', content: '[PPT_FILE:数组与指针_课件_讯飞.pptx]', coverImage: '/api/ppt/slides/数组与指针_课件_讯飞.pptx/slide-1.png', difficulty: 'medium', generatedBy: 'ppt-agent', hallucinationScore: 0.96, createdAt: '2026-07-18T18:12:00' },

  // ===== 结构体章节资源 =====
  { id: 241, type: 'article', title: '结构体与联合体详解', knowledgePoint: '结构体', content: '# 结构体详解\n\n## 结构体定义\n```c\nstruct Student {\n    char name[20];\n    int age;\n    float score;\n};\n```\n\n## 成员访问\n- 结构体变量：`.`运算符\n- 结构体指针：`->`运算符\n\n## 内存对齐\n结构体在内存中的布局遵循对齐原则。', difficulty: 'medium', generatedBy: 'auto-pipeline', hallucinationScore: 0.94, createdAt: '2026-07-14T10:00:00' },
  { id: 242, type: 'code', title: '结构体使用示例', knowledgePoint: '结构体', content: '## 问题描述\n演示 C 语言结构体的典型用法：定义 `struct Student` 存储学生信息（姓名/年龄/成绩），编写独立的 `printStudent` 函数通过**结构体指针**访问成员，展示 `.` 与 `->` 两种成员访问运算符的差异。\n\n## 输入格式\n无输入，程序内置学生信息：姓名「张三」、年龄 20、成绩 95.5，直接运行即可。\n\n## 输出格式\n依次输出 3 行：\n1. `姓名：张三`\n2. `年龄：20`\n3. `成绩：95.5`\n\n## 样例输入 1\n```plaintext\n（无输入）\n```\n\n## 样例输出 1\n```plaintext\n姓名：张三\n年龄：20\n成绩：95.5\n```\n\n## 代码解析\n结构体操作的三个要点：\n1. **结构体定义**：`struct Student { char name[20]; int age; float score; };` 声明了一个新的「聚合类型」，将三个不同类型的成员打包在一起\n2. **初始化语法**：`struct Student stu = {\"张三\", 20, 95.5};` 使用初始化列表按顺序给三个成员赋值（C99 还支持 `.age = 20` 指定初始化）\n3. **指针参数**：`printStudent(struct Student *s)` 使用指针传递结构体，避免大结构体传值的拷贝开销；此时访问成员必须用箭头运算符 `s->name`，等价于 `(*s).name`\n\n```c\n#include <stdio.h>\n#include <string.h>\n\nstruct Student {\n    char name[20];\n    int age;\n    float score;\n};\n\nvoid printStudent(struct Student *s) {\n    printf(\"姓名：%s\\n\", s->name);\n    printf(\"年龄：%d\\n\", s->age);\n    printf(\"成绩：%.1f\\n\", s->score);\n}\n\nint main() {\n    struct Student stu = {\"张三\", 20, 95.5};\n    printStudent(&stu);\n    return 0;\n}\n```\n\n## 改进建议\n- 使用 `typedef struct Student {...} Student;` 定义类型别名，后续声明变量时可以直接写 `Student stu`，减少书写 `struct`\n- 大量学生数据建议改用「结构体数组」或「动态分配的结构体指针数组」，便于排序/遍历\n- 注意 `sizeof(struct Student)` 可能大于成员大小之和（存在内存对齐填充），必要时可用 `#pragma pack(1)` 取消对齐以节省空间', difficulty: 'medium', generatedBy: 'auto-pipeline', hallucinationScore: 0.95, compileStatus: 'PASSED', createdAt: '2026-07-14T10:00:00' },
  { id: 243, type: 'exercise', title: '结构体专项练习', knowledgePoint: '结构体', content: '# 结构体练习题\n\n1. 如何定义一个结构体？\n2. .和->运算符的区别是什么？\n3. 如何用结构体数组存储学生信息？', difficulty: 'medium', generatedBy: 'auto-pipeline', hallucinationScore: 0.94, createdAt: '2026-07-14T10:00:00' },
  { id: 245, type: 'mindmap', title: '结构体知识图谱', knowledgePoint: '结构体', content: '# 结构体知识图谱\n## 基础概念\n### 定义与声明\n- struct关键字\n- 结构体标签名\n- 成员列表定义\n### 成员变量\n- 不同类型成员\n- 成员不可重名\n- 不能包含自身（但可含自身指针）\n### typedef别名\n- typedef简化类型名\n- typedef struct Node {...} Node\n- 提高代码可读性\n## 使用方法\n### 创建实例\n- 声明变量：struct Student stu\n- 初始化列表：{name, age, score}\n- 指定初始化：{.age = 20}\n### 成员访问\n- 结构体变量用点运算符 .\n- 结构体指针用箭头运算符 ->\n- s->age 等价于 (*s).age\n### 嵌套初始化\n- 多层{}嵌套\n- 部分初始化（其余为0）\n## 高级特性\n### 结构体数组\n- 批量管理同类数据\n- 遍历与排序\n- 学生信息管理案例\n### 结构体指针\n- 动态分配结构体内存\n- 函数参数传递（避免拷贝）\n- ->运算符访问成员\n### 内存对齐\n- 对齐规则：成员偏移为大小的整数倍\n- #pragma pack(1)取消对齐\n- sizeof计算的实际大小\n## 实践应用\n### 数据组织\n- 学生信息管理系统\n- 日期时间表示\n- 几何图形坐标\n### 链表实现\n- 节点定义：data + next指针\n- 头插法/尾插法\n- 遍历、插入、删除\n## 联合体与位域\n### 联合体union\n- 所有成员共享同一内存\n- 同一时刻只存一个值\n- 大小取决于最大成员\n### 位域\n- 按位分配内存\n- 节省存储空间\n- 跨平台移植性问题\n## 常见问题\n### 浅拷贝与深拷贝\n- 结构体赋值是浅拷贝\n- 含指针成员时需深拷贝\n- 内存泄漏风险\n### 自引用问题\n- 不能直接含自身实例\n- 可以含自身指针\n- 链表/树结构的实现基础', difficulty: 'medium', generatedBy: 'auto-pipeline', hallucinationScore: 0.93, createdAt: '2026-07-14T10:00:00' },

  // ===== 文件操作章节资源 =====
  { id: 251, type: 'article', title: 'C语言文件操作详解', knowledgePoint: '文件操作', content: '# 文件操作详解\n\n## 文件打开与关闭\n```c\nFILE *fp = fopen(\"file.txt\", \"r\");\nif (fp != NULL) {\n    // 操作文件\n    fclose(fp);\n}\n```\n\n## 读写函数\n- fgetc/fputc：字符读写\n- fgets/fputs：字符串读写\n- fscanf/fprintf：格式化读写\n- fread/fwrite：块读写', difficulty: 'medium', generatedBy: 'auto-pipeline', hallucinationScore: 0.95, createdAt: '2026-07-14T10:00:00' },
  { id: 252, type: 'code', title: '文件读写示例', knowledgePoint: '文件操作', content: '## 问题描述\n演示 C 语言标准库文件操作的完整流程：使用 `fopen` 分别以写入（w）/读取（r）模式打开同一个 `test.txt`，用 `fprintf` 写入一行字符串，再用 `fgets` 读回并打印到控制台；演示每次 fopen 后都必须**判 NULL** + 操作完成后 **fclose** 的健壮写法。\n\n## 输入格式\n无输入，程序自行在工作目录创建 `test.txt` 并写入固定字符串 `Hello, File!\\n`。\n\n## 输出格式\n从文件中读取一行后输出：\n`读取内容：Hello, File!`\n（注意 `fgets` 会连同文件中的换行符 `\\n` 一起读入，所以输出后会自动换行。）\n\n## 样例输入 1\n```plaintext\n（无输入）\n```\n\n## 样例输出 1\n```plaintext\n读取内容：Hello, File!\n```\n\n## 代码解析\n文件操作的标准「打开→操作→关闭」三步法：\n1. **fopen 打开**：`fopen(\"test.txt\", \"w\")` 模式为 `w`（只写，文件不存在则创建，存在则清空原内容）；读模式为 `r`（只读，文件不存在返回 NULL）\n2. **判空保护**：`if (fp != NULL)` 每次 fopen 后检查返回值！无权限/路径不存在/磁盘满等情况都会返回 NULL，不判断会直接段错误\n3. **fclose 关闭**：每个成功打开的文件必须对应一次 fclose，否则会有**文件句柄泄漏**，长时间运行导致无法再打开新文件\n\n读写函数选择：\n- `fprintf(fp, "格式化字符串", ...)` 格式化写入\n- `fgets(str, 100, fp)` 按行读入（最多读 99 个字符 + `\\0`，遇到 `\\n` 或 EOF 停止），读入的内容**含末尾换行**\n\n```c\n#include <stdio.h>\n\nint main() {\n    FILE *fp;\n    char str[100];\n\n    // 写入文件\n    fp = fopen(\"test.txt\", \"w\");\n    if (fp != NULL) {\n        fprintf(fp, \"Hello, File!\\n\");\n        fclose(fp);\n    }\n\n    // 读取文件\n    fp = fopen(\"test.txt\", \"r\");\n    if (fp != NULL) {\n        fgets(str, 100, fp);\n        printf(\"读取内容：%s\", str);\n        fclose(fp);\n    }\n\n    return 0;\n}\n```\n\n## 改进建议\n- 实际工程中若需读写多行文本，建议用循环 `while(fgets(str, sizeof(str), fp) != NULL)` 逐行处理，并检查 `feof(fp)` / `ferror(fp)` 区分 EOF 与读错误\n- 涉及二进制数据（如结构体数组）时改用 `fread/fwrite`，并使用 `\"rb\"/\"wb\"` 二进制模式，避免 Windows 下 `\\r\\n` 自动转换导致数据错乱\n- 打开失败时可通过 `perror(\"fopen失败：\")` 打印更详细的错误原因，便于调试', difficulty: 'medium', generatedBy: 'auto-pipeline', hallucinationScore: 0.94, compileStatus: 'PASSED', createdAt: '2026-07-14T10:00:00' },
  { id: 253, type: 'exercise', title: '文件操作练习题', knowledgePoint: '文件操作', content: '# 文件操作练习\n\n1. fopen打开文件失败返回什么？\n2. 如何判断文件是否到达末尾？\n3. 文本模式和二进制模式的区别？', difficulty: 'medium', generatedBy: 'auto-pipeline', hallucinationScore: 0.94, createdAt: '2026-07-14T10:00:00' },
  { id: 255, type: 'explanation', title: 'fopen模式详解', knowledgePoint: '文件操作', content: '# fopen模式详解\n\n| 模式 | 含义 | 文件不存在 |\n|------|------|-----------|\n| r | 只读 | 出错 |\n| w | 只写 | 创建 |\n| a | 追加 | 创建 |\n| r+ | 读写 | 出错 |\n| w+ | 读写 | 创建 |\n| a+ | 读写追加 | 创建 |\n\n## 常见错误\n- 忘记检查返回值\n- 写入后忘记关闭', difficulty: 'easy', generatedBy: 'auto-pipeline', hallucinationScore: 0.96, createdAt: '2026-07-14T10:00:00' },
  {
    id: 1, type: 'course-video', title: '8.2 指针变量的使用与注意事项',
    knowledgePoint: '指针',
    content: 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=82',
    difficulty: 'hard', generatedBy: 'course-video',
    hallucinationScore: null, hallucinationRisk: null, hallucinationNote: null,
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-06-23T09:30:00'
  },
  { id: 17, type: 'course-video', title: '8.4 指针与数组的关系',
    knowledgePoint: '指针与数组',
    content: 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=84',
    difficulty: 'medium', generatedBy: 'course-video',
    hallucinationScore: null, hallucinationRisk: null, hallucinationNote: null,
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-07-06T16:00:00'
  },
  {
    id: 2, type: 'article', title: '结构体内存对齐详解',
    knowledgePoint: '结构体',
    content: '# 结构体内存对齐\n\n结构体在内存中的布局遵循对齐原则...\n\n## 对齐规则\n1. 第一个成员在偏移量0处\n2. 每个成员相对于首地址的偏移量是该成员大小的整数倍\n3. 结构体总大小是最大成员大小的整数倍\n\n## 示例\n```c\nstruct S {\n    char a;   // 1 byte\n    int b;    // 4 bytes\n    short c;  // 2 bytes\n};\n// sizeof(struct S) = 12\n```',
    difficulty: 'medium', generatedBy: 'auto-pipeline',
    hallucinationScore: 0.88, hallucinationRisk: '低风险', hallucinationNote: 'RAG校验通过',
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-06-23T08:45:00'
  },
  {
    id: 3, type: 'exercise', title: '指针数组练习20题',
    knowledgePoint: '指针',
    content: '# 指针数组专项练习\n\n1. 写出以下代码的输出...',
    difficulty: 'medium', generatedBy: 'auto-pipeline',
    hallucinationScore: 0.85, hallucinationRisk: '低风险', hallucinationNote: 'RAG校验通过',
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-06-23T07:15:00'
  },
  {
    id: 4, type: 'mindmap', title: 'C语言内存模型思维导图',
    knowledgePoint: '内存管理',
    content: '# C语言内存模型\n## 内存分区\n### 代码区(text)\n- 存放程序执行的机器指令\n- 只读属性，防止被意外修改\n- 共享属性，多次运行同一程序共享代码区\n### 数据区(data)\n- 已初始化的全局变量和静态变量\n- data段：非零初始化\n- bss段：零初始化或未初始化的全局/静态变量\n### 堆区(heap)\n- malloc/calloc/realloc动态分配\n- 手动管理：必须配对free\n- 从低地址向高地址增长\n- 碎片化问题\n### 栈区(stack)\n- 局部变量和函数参数\n- 函数调用帧（栈帧）\n- 从高地址向低地址增长\n- 自动管理：函数结束自动回收\n## 动态内存管理\n### malloc\n- 分配指定字节数的内存\n- 返回void*需类型转换\n- 失败返回NULL，必须检查\n### calloc\n- 分配并初始化为0\n- 参数为元素个数和元素大小\n- 适合数组分配\n### realloc\n- 调整已分配内存大小\n- 可能返回新地址（数据被拷贝）\n- 原指针失效，需更新\n### free\n- 释放动态分配的内存\n- free后应将指针置NULL\n- 重复free导致double free错误\n## 指针与内存\n### 指针运算\n- 指针加减以元素大小为步长\n- 指针差值表示元素个数\n- 指针比较仅同数组内有效\n### 多级指针\n- 二级指针指向一级指针\n- 用于动态二维数组\n- 链表操作中修改头指针\n### 函数指针\n- 指向函数的指针变量\n- 回调函数机制\n- 函数指针数组实现跳转表\n### void指针\n- 通用指针类型\n- 不能直接解引用\n- malloc返回void*需转换\n## 内存安全\n### 内存泄漏\n- malloc后未free\n- 循环中重复分配未释放\n- 异常路径未处理free\n### 野指针\n- 指向已释放的内存\n- free后未置NULL\n- 使用前检查NULL\n### 缓冲区溢出\n- 越界写入相邻内存\n- gets函数的安全隐患\n- strncpy的安全替代\n### 栈溢出\n- 递归过深导致栈耗尽\n- 局部数组过大\n- 栈大小限制与ulimit\n## 内存工具\n### valgrind\n- 检测内存泄漏\n- 检测越界访问\n- 检测未初始化使用\n### AddressSanitizer\n- 编译时加入-fsanitize=address\n- 运行时检测内存错误\n- 性能开销较小',
    difficulty: 'hard', generatedBy: 'auto-pipeline',
    hallucinationScore: 0.90, hallucinationRisk: '低风险', hallucinationNote: 'RAG四道门校验通过',
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-06-22T18:30:00'
  },
  {
    id: 5, type: 'code', title: '动态内存分配示例代码',
    knowledgePoint: '动态内存',
    content: '## 问题描述\n使用 C 语言标准库 `malloc` / `free` 完成一个完整的动态数组分配流程：从堆上申请一个长度为 n=5 的 int 数组，按顺序写入 `i*10`（0,10,20,30,40），遍历打印，最后释放内存，展示动态内存的**申请→判空→使用→释放**完整生命周期。\n\n## 输入格式\n无输入，程序内置 `n = 5`，直接运行即可。\n\n## 输出格式\n依次输出 5 行：\n```\narr[0] = 0\narr[1] = 10\narr[2] = 20\narr[3] = 30\narr[4] = 40\n```\n若内存分配失败，仅输出 1 行 `内存分配失败` 并以退出码 1 结束。\n\n## 样例输入 1\n```plaintext\n（无输入）\n```\n\n## 样例输出 1\n```plaintext\narr[0] = 0\narr[1] = 10\narr[2] = 20\narr[3] = 30\narr[4] = 40\n```\n\n## 代码解析\n动态内存的核心三步：\n1. **malloc 申请**：`malloc(n * sizeof(int))` 按字节数分配空间；建议用 `sizeof(*arr)` 写，即使未来类型从 int 改成 double 也不用改这里\n2. **判空使用**：malloc 失败返回 NULL，必须先判空再使用，否则直接解引用会触发段错误\n3. **free 释放**：`free(arr)` 归还空间；释放后指针仍指向旧内存（「野指针」），最佳实践是立刻 `arr = NULL` 防止后续误访问\n\n```c\n#include <stdio.h>\n#include <stdlib.h>\n\nint main() {\n    int n = 5;\n    int *arr = (int*)malloc(n * sizeof(int));\n    if (arr == NULL) {\n        printf(\"内存分配失败\\n\");\n        return 1;\n    }\n    for (int i = 0; i < n; i++) {\n        arr[i] = i * 10;\n    }\n    for (int i = 0; i < n; i++) {\n        printf(\"arr[%d] = %d\\n\", i, arr[i]);\n    }\n    free(arr);\n    arr = NULL;\n    return 0;\n}\n```\n\n## 改进建议\n- 若数组大小需从键盘输入，建议用 `scanf(\"%d\", &n)` 后再 malloc，实现真正的「动态」长度\n- C 语言不强制转换 malloc 的返回值（void* 可隐式转换为任意指针），强制转换仅为兼容 C++ 编译器与部分 lint 规则\n- 可升级为 `realloc` 支持运行期扩容，或使用 `calloc` 自动清零避免未初始化值读入',
    difficulty: 'medium', generatedBy: 'auto-pipeline',
    hallucinationScore: 0.93, hallucinationRisk: '低风险', hallucinationNote: 'RAG校验+编译通过',
    compileStatus: 'PASSED', compileError: null, feedbackType: null,
    createdAt: '2026-06-22T15:00:00'
  },
  {
    id: 6, type: 'explanation', title: '函数指针与回调机制详解',
    knowledgePoint: '函数指针',
    content: '# 函数指针与回调\n\n函数指针是指向函数的指针变量...\n\n## 声明\n```c\nint (*funcPtr)(int, int);\n```\n\n## 回调函数示例\n```c\nvoid process(int *arr, int n, int (*op)(int)) {\n    for (int i = 0; i < n; i++)\n        arr[i] = op(arr[i]);\n}\n```',
    difficulty: 'hard', generatedBy: 'ebbinghaus-review',
    hallucinationScore: 0.87, hallucinationRisk: '低风险', hallucinationNote: 'RAG校验通过（艾宾浩斯复习推送）',
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-06-22T10:20:00'
  },
  {
    id: 7, type: 'summary', title: '第3周学习总结：控制结构',
    knowledgePoint: '控制结构',
    content: '# 第3周学习总结：控制结构\n\n## 一、已掌握知识点\n\n### 1. 分支结构\n- `if-else` 条件判断：单分支、双分支、多分支嵌套\n- `switch-case` 多路选择：注意 `break` 的作用和 `default` 分支\n- 条件运算符 `? :` 的使用场景\n\n### 2. 循环结构\n- `for` 循环：适合已知循环次数的场景\n- `while` 循环：先判断后执行，可能一次都不执行\n- `do-while` 循环：先执行后判断，至少执行一次\n- `break` 跳出当前循环，`continue` 跳过本次迭代\n\n### 3. 关键代码示例\n```c\n// 经典：判断闰年\nif ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0))\n    printf("闰年\\n");\nelse\n    printf("平年\\n");\n\n// 九九乘法表（嵌套循环）\nfor (int i = 1; i <= 9; i++) {\n    for (int j = 1; j <= i; j++)\n        printf("%d×%d=%-4d", j, i, i * j);\n    printf("\\n");\n}\n```\n\n## 二、待加强知识点\n\n### 1. 嵌套循环的逻辑追踪\n- 多层循环变量之间的关系容易混淆\n- 建议：画变量跟踪表，逐步模拟执行流程\n\n### 2. switch语句的 fall-through 特性\n- 忘记写 `break` 会导致意外穿透执行\n- 建议：每个 `case` 后必须写 `break`，除非有意穿透\n\n### 3. 循环边界条件\n- `off-by-one` 错误：循环次数多一次或少一次\n- 建议：明确循环不变量，检查边界值\n\n## 三、常见易错点\n\n| 易错点 | 错误写法 | 正确写法 |\n|--------|---------|----------|\n| if条件赋值 | `if (a = 5)` | `if (a == 5)` |\n| switch漏break | `case 1: ... case 2: ...` | `case 1: ...; break;` |\n| while死循环 | `while(1) {}` 无退出 | 添加 `break` 条件 |\n\n## 四、练习建议\n\n1. **基础题**：完成10道循环嵌套题目（打印图形、穷举法）\n2. **进阶题**：用 `switch` 实现简单计算器\n3. **挑战题**：编写猜数字游戏（结合随机数和循环）\n\n## 五、下周学习预告\n\n将进入**数组与指针**章节，建议预习：\n- 一维数组的声明与初始化\n- 指针的基本概念与取地址运算符 `&`\n- 指针与数组的关系',
    difficulty: 'easy', generatedBy: 'auto-pipeline',
    hallucinationScore: 0.95, hallucinationRisk: '低风险', hallucinationNote: 'RAG四道门校验通过',
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-06-21T20:00:00'
  },
  {
    id: 8, type: 'weakness', title: '指针薄弱点深度分析',
    knowledgePoint: '指针',
    content: '# 指针薄弱点分析\n\n根据你的练习记录，在以下方面存在薄弱...\n\n## 解引用操作\n- 对NULL指针解引用\n- 对已释放内存解引用（悬垂指针）\n\n## 多级指针\n- 二级指针的理解偏差\n- 指针数组与数组指针混淆\n\n## 建议学习路径\n1. 复习一级指针基础\n2. 完成10道指针基础题\n3. 学习指针与数组关系\n4. 进阶多级指针',
    difficulty: 'hard', generatedBy: 'auto-pipeline',
    hallucinationScore: 0.84, hallucinationRisk: '低风险', hallucinationNote: 'RAG校验通过',
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-06-21T16:30:00'
  },
  {
    id: 9, type: 'article', title: 'C语言预处理指令大全',
    knowledgePoint: '预处理',
    content: '# C语言预处理指令\n\n## 宏定义\n```c\n#define PI 3.14159\n#define MAX(a,b) ((a)>(b)?(a):(b))\n```\n\n## 条件编译\n```c\n#ifdef DEBUG\n    printf("debug mode\\n");\n#endif\n```\n\n## 文件包含\n- #include <> 系统路径\n- #include "" 当前目录',
    difficulty: 'easy', generatedBy: 'ebbinghaus-review',
    hallucinationScore: 0.91, hallucinationRisk: '低风险', hallucinationNote: 'RAG四道门校验通过（艾宾浩斯复习推送）',
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-06-20T14:00:00'
  },
  {
    id: 10, type: 'code', title: '链表完整实现',
    knowledgePoint: '链表',
    content: '## 问题描述\n实现一个完整的**带头指针的单链表**：包含 `Node` 结构体定义、`createNode` 创建节点、`insert` 头插法插入、`displayList` 顺序遍历打印、`freeList` 释放全表等核心操作；在 `main` 函数中插入 5 个数据（10, 20, 30, 40, 50），遍历输出后释放链表，覆盖单链表的创建、遍历、内存回收三大生命周期。\n\n## 输入格式\n无输入，程序内置插入数据，直接运行即可。\n\n## 输出格式\n由于使用「头插法」插入，插入顺序 10→20→30→40→50 的实际存储顺序为 50→40→30→20→10，因此输出：\n```\n50 -> 40 -> 30 -> 20 -> 10 -> NULL\n链表已释放，内存回收完成。\n```\n\n## 样例输入 1\n```plaintext\n（无输入）\n```\n\n## 样例输出 1\n```plaintext\n50 -> 40 -> 30 -> 20 -> 10 -> NULL\n链表已释放，内存回收完成。\n```\n\n## 代码解析\n单链表结构的四个核心要素：\n1. **节点结构体**：`typedef struct Node { int data; struct Node* next; } Node;` 使用 `typedef` 简化类型书写；`next` 指针类型必须是 `struct Node*`（typedef 别名在结构体内部尚未生效）\n2. **二级指针插入**：`void insert(Node** head, int data)` 头插法需要修改「头指针本身」的值，因此必须传二级指针 `Node**`，否则只会修改形参，实参的头指针不变\n3. **顺序遍历**：`while (curr != NULL)` 逐个打印数据，循环条件写 `curr != NULL` 而非 `curr->next != NULL`，否则漏掉尾节点\n4. **内存安全释放**：每释放一个节点前必须先保存 `next`，否则 `free(curr)` 后再访问 `curr->next` 即属于 use-after-free 未定义行为\n\n```c\n#include <stdio.h>\n#include <stdlib.h>\n\ntypedef struct Node {\n    int data;\n    struct Node* next;\n} Node;\n\nNode* createNode(int data) {\n    Node* n = (Node*)malloc(sizeof(Node));\n    if (n == NULL) {\n        printf(\"节点分配失败\\n\");\n        exit(1);\n    }\n    n->data = data;\n    n->next = NULL;\n    return n;\n}\n\nvoid insert(Node** head, int data) {\n    Node* n = createNode(data);\n    n->next = *head;\n    *head = n;\n}\n\nvoid displayList(Node* head) {\n    Node* curr = head;\n    while (curr != NULL) {\n        printf(\"%d -> \", curr->data);\n        curr = curr->next;\n    }\n    printf(\"NULL\\n\");\n}\n\nvoid freeList(Node* head) {\n    Node* curr = head;\n    while (curr != NULL) {\n        Node* next = curr->next;\n        free(curr);\n        curr = next;\n    }\n}\n\nint main() {\n    Node* head = NULL;\n    insert(&head, 10);\n    insert(&head, 20);\n    insert(&head, 30);\n    insert(&head, 40);\n    insert(&head, 50);\n    displayList(head);\n    freeList(head);\n    head = NULL;\n    printf(\"链表已释放，内存回收完成。\\n\");\n    return 0;\n}\n```\n\n## 改进建议\n- 可补充 `insertAtTail`（尾插法）、`deleteByValue`（按值删除）、`search`（查找）、`reverse`（反转）等常用操作，形成更完整的单链表 API\n- 头插法会导致顺序反转，若需顺序与插入一致可以改用「尾指针+尾插法」或「带头结点的单链表」\n- 生产级代码建议对所有 malloc 返回值判空（代码中 `createNode` 已处理），并使用 AddressSanitizer 确保无泄漏/越界/野指针',
    difficulty: 'hard', generatedBy: 'auto-pipeline',
    hallucinationScore: 0.89, hallucinationRisk: '低风险', hallucinationNote: 'RAG校验+编译通过',
    compileStatus: 'PASSED', compileError: null, feedbackType: null,
    createdAt: '2026-06-20T09:00:00'
  },
  {
    id: 11, type: 'explanation', title: '文件操作fopen模式详解',
    knowledgePoint: '文件操作',
    content: '# fopen 模式详解\n\n| 模式 | 含义 |\n|------|------|\n| "r" | 只读 |\n| "w" | 只写（覆盖） |\n| "a" | 追加 |\n| "r+" | 读写 |\n| "w+" | 读写（覆盖） |\n| "a+" | 读写（追加） |\n| "rb" | 二进制读 |\n\n## 常见错误\n- 忘记检查fopen返回值\n- 写入后忘记fclose\n- 文本/二进制模式混淆',
    difficulty: 'easy', generatedBy: 'auto-pipeline',
    hallucinationScore: 0.96, hallucinationRisk: '低风险', hallucinationNote: 'RAG四道门校验通过',
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-06-19T11:00:00'
  },
  {
    id: 12, type: 'mindmap', title: 'C语言学习路线思维导图',
    knowledgePoint: 'C语言概述',
    content: '# C语言学习路线\n## 第一阶段：基础入门\n### 变量与数据类型\n- int float double char\n- 变量声明与初始化\n- 类型转换（隐式与强制）\n### 输入输出\n- printf格式化输出\n- scanf格式化输入\n- getchar/putchar字符IO\n### 运算符\n- 算术运算符与优先级\n- 关系与逻辑运算符\n- 位运算与赋值运算符\n## 第二阶段：控制流程\n### 选择结构\n- if-else条件判断\n- else if多分支\n- switch-case与break\n- 三目运算符 ?:\n### 循环结构\n- for循环：已知次数\n- while循环：先判断后执行\n- do-while循环：先执行后判断\n### 控制转移\n- break跳出循环\n- continue跳过本次\n- goto的合理使用场景\n## 第三阶段：核心知识\n### 函数\n- 函数定义与声明\n- 参数传递（值传递/地址传递）\n- 递归与栈的关系\n- 变量的作用域与生命周期\n### 数组\n- 一维数组与初始化\n- 二维数组与矩阵\n- 数组作为函数参数\n- 冒泡排序与选择排序\n### 指针\n- 指针概念与声明\n- 指针与数组的关系\n- 指针作为函数参数\n- 多级指针与函数指针\n### 字符串\n- 字符数组与字符串\n- string.h常用函数\n- strlen/strcpy/strcat/strcmp\n## 第四阶段：进阶提升\n### 结构体\n- 结构体定义与使用\n- 结构体数组与指针\n- ->运算符\n- 结构体与链表\n### 动态内存\n- malloc/calloc/realloc\n- free与内存管理\n- 内存泄漏与野指针\n### 文件操作\n- fopen/fclose文件打开关闭\n- 文本文件读写\n- 二进制文件读写\n- fseek/ftell文件定位\n### 预处理\n- #define宏定义\n- #include文件包含\n- #ifdef条件编译\n- 宏与函数的区别\n## 第五阶段：综合实战\n### 数据结构\n- 链表（单链表/双向链表）\n- 栈与队列\n- 二叉树基础\n### 项目实战\n- 学生信息管理系统\n- 文件加密解密工具\n- 简易计算器\n### 编程规范\n- 代码风格与命名规范\n- 模块化设计思想\n- Makefile基础',
    difficulty: 'easy', generatedBy: 'auto-pipeline',
    hallucinationScore: 0.98, hallucinationRisk: '低风险', hallucinationNote: 'RAG四道门校验通过',
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-06-18T20:00:00'
  },
  {
    id: 13, type: 'course-video', title: '8.1 取地址运算与指针概念',
    knowledgePoint: '指针',
    content: 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=81',
    difficulty: 'medium', generatedBy: 'course-video',
    hallucinationScore: null, hallucinationRisk: null, hallucinationNote: null,
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-06-17T14:00:00'
  },
  {
    id: 14, type: 'course-video', title: '7.5 数组与函数参数传递',
    knowledgePoint: '数组',
    content: 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=75',
    difficulty: 'medium', generatedBy: 'course-video',
    hallucinationScore: null, hallucinationRisk: null, hallucinationNote: null,
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-06-16T09:00:00'
  },
  // ===== 听学博客资源 =====
  { id: 301, type: 'blog', title: '指针入门听学笔记', knowledgePoint: '指针', content: '什么是指针？指针就是存储内存地址的变量。指针的声明、取地址和解引用运算符的使用方法。', difficulty: 'easy', generatedBy: 'one-click', hallucinationScore: 0.95, hallucinationRisk: 'LOW', createdAt: '2026-07-18T10:00:00', audioUrl: '/audio/chapters/指针/指针入门-听学笔记.mp3' },
  { id: 302, type: 'blog', title: '数组基础听学笔记', knowledgePoint: '数组', content: '数组是相同类型元素的集合。一维数组的声明和初始化方法。', difficulty: 'easy', generatedBy: 'one-click', hallucinationScore: 0.93, hallucinationRisk: 'LOW', createdAt: '2026-07-18T10:00:00', audioUrl: '/audio/chapters/数组/数组基础-听学笔记.mp3' },
  { id: 303, type: 'blog', title: '函数基础听学笔记', knowledgePoint: '函数', content: '函数是代码复用的基本单位。函数的定义、声明和调用方法。', difficulty: 'medium', generatedBy: 'one-click', hallucinationScore: 0.93, hallucinationRisk: 'LOW', createdAt: '2026-07-18T10:00:00', audioUrl: '/audio/chapters/函数/函数设计与使用-听学笔记.mp3' },
  { id: 304, type: 'blog', title: '结构体听学笔记', knowledgePoint: '结构体', content: '结构体用于组合不同类型的数据。结构体的定义和成员访问方法。', difficulty: 'medium', generatedBy: 'one-click', hallucinationScore: 0.91, hallucinationRisk: 'LOW', createdAt: '2026-07-18T10:00:00', audioUrl: '/audio/chapters/结构体/结构体与自定义类型-听学笔记.mp3' },
  { id: 305, type: 'blog', title: '控制结构听学笔记', knowledgePoint: '控制结构', content: 'C语言通过条件语句和循环语句控制程序执行流程。if-else、for、while的使用。', difficulty: 'medium', generatedBy: 'one-click', hallucinationScore: 0.92, hallucinationRisk: 'LOW', createdAt: '2026-07-18T10:00:00', audioUrl: '/audio/chapters/控制结构/流程控制-听学笔记.mp3' },
  { id: 306, type: 'blog', title: '动态内存听学笔记', knowledgePoint: '动态内存', content: 'C语言通过malloc/free进行动态内存管理。内存分配和释放的方法。', difficulty: 'hard', generatedBy: 'one-click', hallucinationScore: 0.90, hallucinationRisk: 'LOW', createdAt: '2026-07-18T10:00:00', audioUrl: '/audio/chapters/动态内存/内存管理-听学笔记.mp3' },
  // ===== 学习总结资源（多章节覆盖） =====
  {
    id: 400, type: 'summary', title: '第1周学习总结：基础语法',
    knowledgePoint: '基础语法',
    content: '# 第1周学习总结：C语言基础语法\n\n## 一、已掌握知识点\n\n### 1. C程序结构\n- 每个C程序必须有且仅有一个 `main` 函数作为入口\n- `#include <stdio.h>` 引入标准库头文件，让程序可以使用 `printf` 和 `scanf`\n- 每条语句以分号 `;` 结尾，代码块用花括号 `{ }` 包围\n- `return 0` 表示程序正常退出，非零值表示异常退出\n\n### 2. 数据类型\n- 掌握 `int`（整型，4字节）、`float`（单精度浮点，4字节）、`double`（双精度浮点，8字节）、`char`（字符型，1字节）四种基本类型\n- 理解 `sizeof` 运算符获取类型占用字节数，`sizeof(int)` 返回 4\n- 了解 `unsigned`（无符号）、`signed`（有符号，默认）、`short`（短整型）、`long`（长整型）类型修饰符的作用\n- 知道 `%d`（int）、`%f`（float）、`%lf`（double）、`%c`（char）等格式符\n\n### 3. 变量与常量\n- 变量声明语法：`类型 变量名 = 初始值;`，如 `int age = 20;`\n- 变量命名规则：字母/下划线开头，区分大小写，不能使用关键字，不能以数字开头\n- 两种常量定义方式：`#define PI 3.14`（宏常量，纯文本替换）和 `const double PI = 3.14;`（常变量，有类型检查）\n- 理解局部变量与全局变量的作用域区别：局部变量仅在函数内可见，全局变量整个文件可见\n\n### 4. 运算符\n- 算术运算符：`+ - * / %`，特别注意整数除法截断（`5/2=2`，不是 `2.5`）\n- 自增自减：`++i` 前置（先加后用）与 `i++` 后置（先用后加）的区别\n- 关系运算符：`== != > < >= <=`，特别注意 `==` 与 `=` 的区别\n- 逻辑运算符：`&& || !` 及其短路特性\n- 位运算符：`& | ^ ~ << >>`（进阶内容，了解即可）\n- 运算符优先级与结合性：善用括号 `()` 避免歧义，`(a + b) * c` 比 `a + b * c` 更清晰\n\n### 5. 输入输出\n- `printf` 格式化输出：`%d`（整数）、`%f`（浮点）、`%c`（字符）、`%s`（字符串）、`%p`（地址）、`%.2f`（保留2位小数）、`%5d`（宽度控制）\n- `scanf` 格式化输入：**变量前必须加 `&` 取地址符**（如 `scanf("%d", &age)`），字符串除外\n- 转义字符：`\\n`（换行）、`\\t`（制表符）、`\\\\`（反斜杠）、`\\0`（空字符）、`\\"`（双引号）、`%%`（百分号）\n\n### 6. 类型转换\n- 隐式转换（自动）：`char → int → long → float → double`，小类型自动提升为大类型\n- 强制转换（手动）：`(目标类型)表达式`，如 `(float)a / b` 避免整数除法\n\n### 7. 关键代码示例\n\n```c\n#include <stdio.h>\n\nint main() {\n    int a, b;\n\n    // 输入两个整数\n    printf("请输入两个整数: ");\n    scanf("%d %d", &a, &b);\n\n    // 四则运算\n    printf("和 = %d\\n", a + b);\n    printf("差 = %d\\n", a - b);\n    printf("积 = %d\\n", a * b);\n    printf("商 = %.2f\\n", (float)a / b);  // 注意强制转换避免整数除法\n\n    // 判断大小\n    if (a > b) {\n        printf("%d 大于 %d\\n", a, b);\n    } else if (a < b) {\n        printf("%d 小于 %d\\n", a, b);\n    } else {\n        printf("两数相等\\n");\n    }\n\n    // 打印各类型大小\n    printf("\\n--- 各类型大小 ---\\n");\n    printf("int: %zu 字节\\n", sizeof(int));\n    printf("float: %zu 字节\\n", sizeof(float));\n    printf("double: %zu 字节\\n", sizeof(double));\n    printf("char: %zu 字节\\n", sizeof(char));\n\n    return 0;\n}\n```\n\n## 二、待加强知识点\n\n### 1. 整数除法陷阱\n- `5 / 2` 结果为 `2`（截断小数），不是 `2.5`\n- 解决方案：`(float)5 / 2` 或 `5.0 / 2` 或 `(double)5 / 2`\n- 记忆口诀："整数除整数，结果还是整数，小数部分被丢弃"\n- 特别注意：`(float)(a / b)` 是错误写法，因为先做整数除法再转换，结果仍是整数\n\n### 2. scanf 的细节\n- `scanf("%d", &a)` 中 `&` 最容易忘记，导致段错误或数据写入错误位置\n- `scanf("%s", str)` 遇到空格会停止读取，用 `fgets(str, sizeof(str), stdin)` 读取含空格的字符串\n- 读取字符后缓冲区可能残留换行符，用 `while(getchar() != \'\\n\');` 清除\n- `scanf` 的返回值表示成功读取的变量个数，可以用于判断输入是否合法\n\n### 3. 自增自减的微妙之处\n- `int b = a++`：先将 a 的值赋给 b，a 再加 1\n- `int b = ++a`：a 先加 1，再将新值赋给 b\n- 在复杂表达式中混用自增（如 `a = a++ + ++a`）可能导致**未定义行为**，不同编译器结果不同\n\n### 4. 位运算（进阶）\n- 异或 `^` 可用于交换两个变量（不借助临时变量）：`a ^= b; b ^= a; a ^= b;`\n- 左移 `<<` 相当于乘以 2 的幂次，右移 `>>` 相当于除以 2 的幂次\n- 位运算效率极高，在嵌入式开发和底层编程中广泛使用\n\n### 5. 格式符的精确控制\n- `%.2f` 保留2位小数；`%5d` 宽度5右对齐；`%-5d` 宽度5左对齐\n- `%05d` 宽度5前面补零；`%10.2f` 总宽度10保留2位小数\n- 格式符与参数类型不匹配会导致未定义行为（如 `printf("%d", 3.14)` 输出乱码）\n\n## 三、常见易错点\n\n| 序号 | 易错点 | 错误写法 | 正确写法 | 原因 |\n|------|--------|---------|----------|------|\n| 1 | 整数除法 | `float f = 1/2;` → 0.0 | `float f = 1.0/2;` → 0.5 | 整数除法截断小数 |\n| 2 | scanf 漏 & | `scanf("%d", a)` | `scanf("%d", &a)` | 必须传地址 |\n| 3 | 格式符不匹配 | `printf("%f", 10)` | `printf("%d", 10)` | 类型与格式符对应 |\n| 4 | = 与 == 混淆 | `if (a = 5)` | `if (a == 5)` | 赋值表达式永远为真 |\n| 5 | 忘记分号 | `printf("hello")` | `printf("hello");` | 语句必须以分号结尾 |\n| 6 | 字符与字符串 | `if (c == "y")` | `if (c == \'y\')` | 单引号是字符，双引号是字符串 |\n| 7 | 中文标点符号 | `printf("你好");` | `printf("你好");` | 必须使用英文标点符号 |\n| 8 | 变量未初始化 | `int a; printf("%d", a);` | `int a = 0;` | 局部变量初始值不确定 |\n\n## 四、练习建议\n\n### 基础题（必做，建议完成时间：2小时）\n\n1. **Hello World 变体**：编写程序分别打印你的姓名、学号、班级，每行一条信息\n2. **圆面积计算**：输入半径 r，计算并输出圆的面积（π×r²）和周长（2×π×r），π 用 3.14159\n3. **温度转换**：输入摄氏温度 C，转换为华氏温度 F = C × 9/5 + 32，保留1位小数输出\n4. **数字拆分**：输入一个三位数（如 789），分别输出其百位、十位、个位数字\n5. **数据类型大小**：用 `sizeof` 打印 `int`、`float`、`double`、`char`、`short`、`long`、`long long` 在当前平台上的字节数\n\n### 进阶题（选做，建议完成时间：3小时）\n\n6. **三数最值**：输入三个整数，找出并输出最大值和最小值\n7. **简单计算器**：输入两个数和运算符（+、-、*、/），输出计算结果，注意除数为零的处理\n8. **字符加密**：输入一个字符，输出其 ASCII 码偏移 3 位后的字符（如 \'A\'→\'D\'，\'x\'→\'{\'）\n9. **时间换算**：输入总秒数，转换为"时:分:秒"格式输出（如 3661 秒 → 1小时1分1秒）\n\n### 挑战题（选做，建议完成时间：4小时）\n\n10. **进制转换**：输入一个十进制整数，输出其二进制、八进制、十六进制表示（提示：用 `%x`、`%o` 格式符）\n11. **简易收银**：输入商品单价和数量，计算总价，根据总价计算折扣（满100减10，满200减30，满500减100），输出应付金额\n\n## 五、下周学习预告\n\n将进入**流程控制**章节，建议提前预习以下内容：\n\n### 选择结构\n- `if-else` 条件判断语句（单分支、双分支、多分支嵌套）\n- `switch-case` 多路选择（注意 `break` 的作用和 `default` 分支）\n- 条件运算符（三目运算符）`? :` 的简单用法\n\n### 循环结构\n- `for` 循环：适合已知循环次数的场景，三要素「初始化; 条件; 更新」\n- `while` 循环：先判断后执行，条件不满足则一次都不执行\n- `do-while` 循环：先执行后判断，至少执行一次（末尾有分号）\n\n### 跳转语句\n- `break`：跳出当前循环或 switch\n- `continue`：跳过本次循环剩余代码，进入下一次迭代\n- `goto`：了解即可，不建议滥用\n\n### 预习建议\n- 尝试编写九九乘法表（嵌套循环）\n- 思考如何用 switch 实现星期几的判断\n- 用循环实现 1+2+...+100 的累加（三种循环各写一遍）', difficulty: 'easy', generatedBy: 'auto-pipeline',
    hallucinationScore: 0.95, hallucinationRisk: '低风险', hallucinationNote: 'RAG四道门校验通过',
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-07-01T20:00:00'
  },
  {
    id: 401, type: 'summary', title: '第4周学习总结：数组与指针',
    knowledgePoint: '数组与指针',
    content: '# 第4周学习总结：数组与指针\n\n## 一、已掌握知识点\n\n### 1. 一维数组\n- 声明：`int arr[10];` 声明时大小必须为常量\n- 初始化：`int arr[] = {1, 2, 3, 4, 5};`\n- 访问：下标从 `0` 开始，到 `n-1` 结束\n- 数组名本质是指向第一个元素的指针\n\n### 2. 二维数组\n- 声明：`int matrix[3][4];`：3行4列\n- 初始化：`int m[][3] = {{1,2,3}, {4,5,6}};`\n- 访问：`matrix[i][j]`，先行后列\n\n### 3. 指针基础\n- 定义：`int *p = &a;`：p 存储变量 a 的地址\n- 解引用：`*p` 获取 p 指向的值\n- 取地址：`&a` 获取变量 a 的地址\n- 指针类型决定了解引用时读取的字节数\n\n### 4. 指针与数组\n- `arr[i]` 等价于 `*(arr + i)`\n- `arr` 等价于 `&arr[0]`\n- 指针算术：`p+1` 移动一个元素的大小（非1字节）\n\n### 5. 关键代码示例\n```c\n// 用指针遍历数组\nint arr[] = {10, 20, 30, 40, 50};\nint *p = arr;\nfor (int i = 0; i < 5; i++) {\n    printf("arr[%d] = %d\\n", i, *(p + i));\n}\n\n// 二维数组求和\nint matrix[3][4] = {{1,2,3,4},{5,6,7,8},{9,10,11,12}};\nint sum = 0;\nfor (int i = 0; i < 3; i++)\n    for (int j = 0; j < 4; j++)\n        sum += matrix[i][j];\nprintf("总和: %d\\n", sum);\n```\n\n## 二、待加强知识点\n\n### 1. 指针数组和数组指针\n- `int *arr[10]`：指针数组（10个int指针）\n- `int (*arr)[10]`：数组指针（指向含10个int的数组）\n- 二者含义完全不同，容易混淆\n\n### 2. 二维数组与指针\n- `int (*p)[4] = matrix;`：正确的二维数组指针写法\n- `int **p = matrix;`：错误，类型不匹配\n\n## 三、常见易错点\n\n| 易错点 | 错误写法 | 正确写法 |\n|--------|---------|----------|\n| 数组越界 | `arr[10]`（声明10个） | 下标最大为 `arr[9]` |\n| 野指针 | `int *p; *p = 10;` | 先初始化：`int a; int *p = &a;` |\n| 指针类型混淆 | `int *p = matrix;` | `int (*p)[4] = matrix;` |\n| sizeof数组 | 函数内 `sizeof(arr)` | 传参后退化为指针，大小为8 |\n\n## 四、练习建议\n\n1. **基础题**：用指针实现数组逆序\n2. **进阶题**：矩阵转置（二维数组）\n3. **挑战题**：用指针数组实现字符串排序\n\n## 五、下周学习预告\n\n将进入**函数**章节，建议预习：\n- 函数的定义、声明与调用\n- 参数传递：值传递与指针传递\n- 递归的基本思想',
    difficulty: 'medium', generatedBy: 'auto-pipeline',
    hallucinationScore: 0.94, hallucinationRisk: '低风险', hallucinationNote: 'RAG四道门校验通过',
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-07-08T20:00:00'
  },
  {
    id: 402, type: 'summary', title: '第5周学习总结：函数',
    knowledgePoint: '函数',
    content: '# 第5周学习总结：函数\n\n## 一、已掌握知识点\n\n### 1. 函数定义与调用\n- 函数三要素：返回类型、函数名、参数列表\n- 定义：`int add(int a, int b) { return a + b; }`\n- 声明（原型）：`int add(int, int);`：放在调用前\n- 调用：`int result = add(3, 5);`\n\n### 2. 参数传递\n- **值传递**：实参的值复制给形参，形参不影响实参\n- **指针传递**：传递地址，可在函数内修改实参\n- **数组传递**：退化为指针，需额外传长度\n\n### 3. 变量作用域\n- 局部变量：函数内定义，仅在函数内有效\n- 全局变量：函数外定义，整个文件可见\n- `static` 局部变量：函数结束后不销毁，下次调用保留值\n\n### 4. 递归\n- 基本思想：函数调用自身，必须有终止条件\n- 经典案例：阶乘、斐波那契数列\n- 注意：递归深度过大可能导致栈溢出\n\n### 5. 关键代码示例\n```c\n// 值传递与指针传递\nvoid swap_value(int a, int b) {  // 无效交换\n    int t = a; a = b; b = t;\n}\nvoid swap_ptr(int *a, int *b) {  // 有效交换\n    int t = *a; *a = *b; *b = t;\n}\n\n// 递归：计算阶乘\nint factorial(int n) {\n    if (n <= 1) return 1;       // 终止条件\n    return n * factorial(n - 1); // 递归调用\n}\n```\n\n## 二、待加强知识点\n\n### 1. 函数指针\n- 定义：`int (*fp)(int, int) = add;`\n- 调用：`fp(3, 5)` 或 `(*fp)(3, 5)`\n- 用途：回调函数、qsort排序的比较函数\n\n### 2. 递归效率\n- 斐波那契数列 `fib(5)` 会重复计算 `fib(3)` 多次\n- 建议采用记忆化或迭代方式优化\n\n## 三、常见易错点\n\n| 易错点 | 说明 | 正确做法 |\n|--------|------|----------|\n| 忘记声明 | 函数定义在调用之后 | 前面加函数原型声明 |\n| 值传递误解 | 误认为swap能交换实参 | 用指针传递 |\n| 返回局部指针 | `return &local_var;` | 返回值或动态分配内存 |\n| 递归无终止 | 缺少终止条件 | 必须有终止条件 |\n\n## 四、练习建议\n\n1. **基础题**：编写函数判断素数、计算最大公约数\n2. **进阶题**：用递归实现汉诺塔\n3. **挑战题**：用函数指针实现通用排序函数\n\n## 五、下周学习预告\n\n将进入**结构体**章节，建议预习：\n- 结构体的定义与初始化\n- 结构体数组与指针\n- `typedef` 的使用',
    difficulty: 'medium', generatedBy: 'auto-pipeline',
    hallucinationScore: 0.94, hallucinationRisk: '低风险', hallucinationNote: 'RAG四道门校验通过',
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-07-15T20:00:00'
  },
  {
    id: 403, type: 'summary', title: '第6周学习总结：结构体',
    knowledgePoint: '结构体',
    content: '# 第6周学习总结：结构体\n\n## 一、已掌握知识点\n\n### 1. 结构体定义与使用\n- 定义：`struct Student { char name[20]; int age; float score; };`\n- 声明变量：`struct Student s1 = {"张三", 20, 85.5};`\n- 成员访问：`s1.age`（变量）、`s1->age`（指针）\n- 嵌套结构体：结构体成员可以是另一个结构体\n\n### 2. typedef 别名\n- `typedef struct Student { ... } Student;`\n- 之后可直接用 `Student s1;` 代替 `struct Student s1;`\n\n### 3. 结构体数组\n- 声明：`Student class[30];`\n- 访问：`class[i].name`、`class[i].age`\n- 适合管理批量同类型数据\n\n### 4. 结构体指针\n- `Student *p = &s1;`\n- 通过指针访问成员：`p->age` 或 `(*p).age`\n- 常用于动态分配结构体内存\n\n### 5. 关键代码示例\n```c\ntypedef struct {\n    char name[20];\n    int age;\n    float score;\n} Student;\n\n// 结构体数组排序（按成绩降序）\nvoid sortByScore(Student arr[], int n) {\n    for (int i = 0; i < n - 1; i++)\n        for (int j = 0; j < n - 1 - i; j++)\n            if (arr[j].score < arr[j+1].score) {\n                Student t = arr[j];\n                arr[j] = arr[j+1];\n                arr[j+1] = t;\n            }\n}\n```\n\n## 二、待加强知识点\n\n### 1. 内存对齐\n- 结构体大小不等于各成员大小之和（存在填充字节）\n- `sizeof(Student)` 可能大于 `20 + 4 + 4`\n- `#pragma pack(1)` 可取消对齐，但影响性能\n\n### 2. 联合体 (union)\n- 所有成员共享同一块内存，大小=最大成员\n- 同一时刻只能使用一个成员\n\n## 三、常见易错点\n\n| 易错点 | 错误写法 | 正确写法 |\n|--------|---------|----------|\n| 字符串赋值 | `s1.name = "张三"` | `strcpy(s1.name, "张三")` |\n| 指针成员访问 | `p.age` (p是指针) | `p->age` |\n| 忘记typedef | `Student s1;` (未typedef) | `struct Student s1;` |\n| 返回局部结构体地址 | `return &s1;` | 返回值而非地址 |\n\n## 四、练习建议\n\n1. **基础题**：定义学生结构体，输入3名学生信息并输出\n2. **进阶题**：实现学生成绩管理系统（增删改查）\n3. **挑战题**：用结构体+指针实现链表\n\n## 五、下周学习预告\n\n将进入**文件操作**章节，建议预习：\n- `fopen`/`fclose` 文件打开与关闭\n- `fread`/`fwrite` 二进制读写\n- `fprintf`/`fscanf` 文本读写',
    difficulty: 'medium', generatedBy: 'auto-pipeline',
    hallucinationScore: 0.93, hallucinationRisk: '低风险', hallucinationNote: 'RAG四道门校验通过',
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-07-22T20:00:00'
  },
  {
    id: 404, type: 'summary', title: '第7周学习总结：文件操作',
    knowledgePoint: '文件操作',
    content: '# 第7周学习总结：文件操作\n\n## 一、已掌握知识点\n\n### 1. 文件打开与关闭\n- `FILE *fp = fopen("test.txt", "r");`：打开文件\n- `fclose(fp);`：关闭文件，释放资源\n- 打开模式：`r`(读)、`w`(写/覆盖)、`a`(追加)、`r+`(读写)、`b`(二进制)\n- 打开失败返回 `NULL`，必须检查\n\n### 2. 文本文件读写\n- `fprintf(fp, "%d %s", num, str);`：格式化写入\n- `fscanf(fp, "%d %s", &num, str);`：格式化读取\n- `fgets(buf, size, fp);`：读取一行\n- `fputs(str, fp);`：写入字符串\n\n### 3. 二进制文件读写\n- `fwrite(&data, sizeof(Data), 1, fp);`：写入一块数据\n- `fread(&data, sizeof(Data), 1, fp);`：读取一块数据\n- 适合结构体等复杂数据的批量存储\n\n### 4. 文件定位\n- `feof(fp)`：判断是否到达文件末尾\n- `rewind(fp)`：回到文件开头\n- `fseek(fp, offset, SEEK_SET);`：定位到指定位置\n- `ftell(fp)`：获取当前位置\n\n### 5. 关键代码示例\n```c\n// 读写学生信息到二进制文件\ntypedef struct {\n    char name[20];\n    int age;\n    float score;\n} Student;\n\n// 写入\nFILE *fp = fopen("students.dat", "wb");\nif (!fp) { perror("打开失败"); return 1; }\nStudent s = {"张三", 20, 85.5};\nfwrite(&s, sizeof(Student), 1, fp);\nfclose(fp);\n\n// 读取\nfp = fopen("students.dat", "rb");\nwhile (fread(&s, sizeof(Student), 1, fp) == 1) {\n    printf("%s %d %.1f\\n", s.name, s.age, s.score);\n}\nfclose(fp);\n```\n\n## 二、待加强知识点\n\n### 1. feof的使用时机\n- `feof` 在读取失败后才返回真，不能用于预判断\n- 正确做法：先读后判断 `while (fread(...) == 1)`\n\n### 2. 文本模式与二进制模式\n- Windows下文本模式会转换 `\\n` 与 `\\r\\n`\n- 二进制模式不转换，适合非文本数据\n\n## 三、常见易错点\n\n| 易错点 | 错误写法 | 正确写法 |\n|--------|---------|----------|\n| 不检查fopen | 直接使用fp | `if (!fp) { ... }` |\n| 忘记fclose | 函数结束不关闭 | 每个fopen对应fclose |\n| feof误用 | `while(!feof(fp))` | `while (fread(...) == 1)` |\n| 模式错误 | 写二进制用`"w"` | 用`"wb"` |\n\n## 四、练习建议\n\n1. **基础题**：将用户输入写入文本文件并读回显示\n2. **进阶题**：用二进制文件实现学生信息存储与查询\n3. **挑战题**：实现文件复制程序（支持文本和二进制）\n\n## 五、学期回顾\n\n已完成C语言核心知识的学习，建议：\n- 系统复习所有章节总结\n- 完成综合项目：学生成绩管理系统（文件存储）\n- 参加蓝桥杯等编程竞赛检验水平',
    difficulty: 'medium', generatedBy: 'auto-pipeline',
    hallucinationScore: 0.93, hallucinationRisk: '低风险', hallucinationNote: 'RAG四道门校验通过',
    compileStatus: null, compileError: null, feedbackType: null,
    createdAt: '2026-07-28T20:00:00'
  },
]

export const demoChatHistory = [
  {
    id: 1001, role: 'assistant', agentName: 'AI导学助理',
    content: '你好，我是你的AI导学助理。\n\n根据你的学习画像，我注意到你在**指针**和**内存管理**方面需要加强。我已经为你自动生成了学习资源，你可以去资源中心查看。\n\n有什么C语言问题，随时问我。',
    onboarding: false, onboardingComplete: false, options: null,
    createdAt: '2026-06-23T10:30:00'
  },
  {
    id: 1002, role: 'user',
    content: 'C语言中指针和数组有什么区别？',
    createdAt: '2026-06-23T10:31:00'
  },
  {
    id: 1003, role: 'assistant', agentName: 'AI导师',
    content: '好问题，指针和数组是C语言中容易混淆的概念，但它们有本质区别：\n\n## 核心区别\n\n| 特性 | 数组 | 指针 |\n|------|------|------|\n| 本质 | 一块连续内存 | 存储地址的变量 |\n| sizeof | 整个数组大小 | 4或8字节 |\n| 赋值 | 不能赋值 | 可以改变指向 |\n| &操作 | 取数组地址 | 取指针变量地址 |\n\n## 代码示例\n```c\nint arr[5] = {1,2,3,4,5};\nint *p = arr;  // p指向arr[0]\n\n// arr = p;    错误，数组名是常量\n// p = arr;    正确，指针可以指向数组\n```\n\n需要我做几道指针的练习题吗？',
    onboarding: false, onboardingComplete: false, options: ['来几道指针题练练', '先给我讲讲内存管理', '看看我的学习画像'],
    createdAt: '2026-06-23T10:31:05'
  },
  {
    id: 1004, role: 'user',
    content: '来几道指针题练练',
    createdAt: '2026-06-23T10:32:00'
  }
]

export const demoWeakAreas = [
  { name: '指针', level: 'high', hint: '解引用与多级指针混淆' },
  { name: '内存管理', level: 'high', hint: 'malloc/free配对遗漏' },
  { name: '结构体', level: 'medium', hint: '内存对齐计算错误' },
  { name: '文件操作', level: 'medium', hint: 'fopen模式选择混淆' },
  { name: '递归', level: 'low', hint: '终止条件设置不当' }
]

export const demoAchievements = [
  { id: 1, name: '初识C语言', icon: '初', desc: '完成AI导学面试', unlocked: true, date: '2026-06-17' },
  { id: 2, name: '连续学习', icon: '连', desc: '连续学习7天', unlocked: true, date: '2026-06-23' },
  { id: 3, name: '初露锋芒', icon: '锋', desc: '完成20道练习题', unlocked: true, date: '2026-06-22' },
  { id: 4, name: '知识探索者', icon: '查', desc: '浏览10篇学习资源', unlocked: true, date: '2026-06-21' },
  { id: 5, name: '代码达人', icon: '码', desc: '编写100行C代码', unlocked: false, progress: 72 },
  { id: 6, name: '学霸之路', icon: '优', desc: '知识水平达到80分', unlocked: false, progress: 62 },
]

/**
 * 一年 GitHub 风格热力图数据（2025-06-24 ~ 2026-06-23）
 * 学习活跃度：level 0~4 按 count 分布
 * 0:0  1:1~29  2:30~59  3:60~99  4:100+
 */
function generateYearHeatmap() {
  const data = []
  const start = new Date('2025-06-24')
  const end = new Date('2026-06-23')
  const d = new Date(start)
  // 种子确保每次生成一致
  let seed = 42
  function rnd() { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return (seed >>> 0) / 0x7fffffff }
  function pick(arr) { return arr[Math.floor(rnd() * arr.length)] }

  while (d <= end) {
    const ds = d.toISOString().split('T')[0]
    const wd = d.getDay() // 0=Sun
    const m = d.getMonth()
    let count = 0

    // Weekday与weekend
    if (wd === 0 || wd === 6) {
      if (rnd() < 0.45) count = pick([10,15,20,25,30,35])
    } else {
      if (rnd() < 0.82) count = pick([15,20,25,30,35,40,45,50,55,60,65,70,75,80,90,100,110,120,130])
    }

    // Season boost
    if (m === 4 || m === 5) { count = count > 0 ? Math.min(count + 20, 150) : 0 }   // 考试季（5~6月）
    if (m === 0 || m === 1) { if (count > 0 && rnd() < 0.35) count = 0 }             // 寒假

    data.push({ date: ds, count })
    d.setDate(d.getDate() + 1)
  }
  return data
}

export const demoHeatmap = {
  data: generateYearHeatmap()
}

export const demoStats = {
  knowledgeLevel: 62,
  progress: 45,
  totalQuestions: 28,
  errorCount: 16,
  completedTests: 3,
  studyHours: 5.7
}

export const demoAnalysis = `# AI 学习分析报告

> 生成时间：2026年6月23日 | RAG 增强分析

## 一、学习概览

| 指标 | 数值 | 评级 |
|------|------|------|
| 知识水平 | 62分 | ⭐⭐⭐ 中等 |
| 学习进度 | 45% | ⭐⭐⭐ 进行中 |
| 练习总量 | 28题 | ⭐⭐ 需加强 |
| 连续学习 | 7天 | ⭐⭐⭐⭐ 优秀 |

## 二、薄弱环节分析

根据你最近的对话和练习情况，AI 识别出以下薄弱点：

1. **指针（严重）**：多级指针和解引用操作存在概念混淆，建议从一级指针重新梳理
2. **内存管理（严重）**：malloc/free 配对使用意识不足，需重点练习
3. **结构体（中等）**：内存对齐计算存在偏差
4. **递归（轻微）**：递归终止条件设计不够熟练

## 三、学习建议

1. **本周重点**：集中攻克指针专题，完成「指针数组练习20题」
2. **动手实践**：每天至少写30行代码，强化内存管理意识
3. **学习路径**：建议按 数组→指针→动态内存→结构体 的顺序推进
4. **定期复习**：系统已开启艾宾浩斯遗忘曲线提醒，请留意复习推送

## 四、预期目标

如果保持当前学习节奏（每天约1小时），预计 **2周后知识水平可达75分**，基本掌握C语言核心内容。`

export const demoQuestions = [
  {
    id: 1,
    knowledgePoint: '指针',
    question: '以下代码的输出是什么？\n```c\nint a = 10;\nint *p = &a;\n*p = 20;\nprintf("%d", a);\n```',
    options: ['A. 10', 'B. 20', 'C. 编译错误', 'D. 随机值'],
    answer: 1,
    ragExplanation: '指针 p 存储了变量 a 的地址，*p = 20 相当于通过指针修改了 a 的值。这是指针最基本的用法——间接访问。',
    ragUsed: true
  },
  {
    id: 2,
    knowledgePoint: '数组',
    question: '以下代码的输出是什么？\n```c\nint arr[] = {1,2,3,4,5};\nprintf("%d", arr[2]);\n```',
    options: ['A. 1', 'B. 2', 'C. 3', 'D. 4'],
    answer: 2,
    ragExplanation: '数组下标从0开始，arr[0]=1, arr[1]=2, arr[2]=3。这是数组索引的基本概念。',
    ragUsed: true
  },
  {
    id: 3,
    knowledgePoint: '动态内存',
    question: '使用 malloc 分配内存后，应该用什么函数释放？',
    options: ['A. delete', 'B. release', 'C. free', 'D. dealloc'],
    answer: 2,
    ragExplanation: 'C语言中使用 malloc/calloc/realloc 分配的内存必须用 free() 释放。忘记释放会导致内存泄漏。',
    ragUsed: true
  },
  {
    id: 4,
    knowledgePoint: '指针',
    question: '对于 int *p, *q; 以下哪个操作是合法的？',
    options: ['A. p + q', 'B. p - q', 'C. p * q', 'D. p / q'],
    answer: 1,
    ragExplanation: '两个指针可以相减，得到它们之间的元素个数（以指针类型的大小为单位）。指针相加、相乘、相除都是非法操作。',
    ragUsed: true
  },
  {
    id: 5,
    knowledgePoint: '结构体',
    question: '以下结构体占多少字节？（假设32位系统）\n```c\nstruct S {\n    char c;\n    int i;\n    short s;\n};\n```',
    options: ['A. 7', 'B. 8', 'C. 12', 'D. 16'],
    answer: 2,
    ragExplanation: '结构体存在内存对齐：char(1字节)后padding 3字节，int(4字节)，short(2字节)后padding 2字节使总大小为4的倍数。总计12字节。',
    ragUsed: true
  },
  {
    id: 6,
    knowledgePoint: '函数',
    question: '以下关于C语言函数的说法，哪个是错误的？',
    options: ['A. 函数可以返回指针', 'B. 函数参数默认是按值传递', 'C. 函数内部可以定义另一个函数', 'D. 函数可以递归调用'],
    answer: 2,
    ragExplanation: 'C语言不允许在函数内部定义另一个函数（不支持嵌套函数定义）。A、B、D都是正确的：可以返回指针、默认传值、支持递归。',
    ragUsed: true
  },
  {
    id: 7,
    knowledgePoint: '文件操作',
    question: '使用 fopen("test.txt", "w") 打开文件，如果文件已存在会发生什么？',
    options: ['A. 追加到文件末尾', 'B. 打开失败', 'C. 清空原文件内容', 'D. 读取文件内容'],
    answer: 2,
    ragExplanation: '"w" 模式（write）会清空已有文件的内容，从头开始写入。如果要追加，应使用 "a" 模式。使用前建议检查 fopen 返回值是否为 NULL。',
    ragUsed: true
  },
  {
    id: 8,
    knowledgePoint: '内存管理',
    question: '以下代码有什么问题？\n```c\nint *p = malloc(sizeof(int)*5);\np[5] = 10;\nfree(p);\n```',
    options: ['A. 没有包含头文件', 'B. 数组越界访问', 'C. free后未置NULL', 'D. 以上都是'],
    answer: 1,
    ragExplanation: 'malloc分配了5个int（索引0-4），但 p[5] 访问了第6个元素，造成数组越界。这可能导致未定义行为（崩溃或数据损坏）。',
    ragUsed: true
  }
]

export const demoPath = {
  id: 1,
  status: 'active',
  currentStep: 3,
  totalSteps: 5,
  steps: `# AI 个性化学习路径

## 阶段1：基础语法 已完成
- 变量与数据类型
- 输入输出函数
- 运算符与表达式
- 预计时间：3天

## 阶段2：控制结构 已完成
- if/else 分支
- switch 多分支
- for/while/do-while 循环
- 预计时间：4天

## 阶段3：函数与数组 进行中
- 函数定义与调用
- 一维/多维数组
- 字符串处理
- 预计时间：5天

## 阶段4：指针与内存 ⏳ 待学习
- 指针基础
- 指针与数组
- 动态内存分配
- 预计时间：6天

## 阶段5：结构体与文件 ⏳ 待学习
- 结构体定义与使用
- 链表基础
- 文件读写操作
- 预计时间：5天`
}

export const demoCalendar = {
  '2026-06-17': true,
  '2026-06-18': true,
  '2026-06-19': true,
  '2026-06-20': true,
  '2026-06-21': true,
  '2026-06-22': true,
  '2026-06-23': true
}

// 学习动态时间线
export const demoTimeline = [
  { text: '完成「靶向练题」8道', time: '10分钟前' },
  { text: 'AI生成了「指针数组练习」', time: '2小时前' },
  { text: '学习画像已更新', time: '3小时前' },
  { text: '开始学习「指针与内存管理」', time: '昨天' }
]

// RAG 监控演示数据
export const demoRagMonitor = {
  overview: {
    totalQueries: 136, gate1Passed: 116, gate1Rejected: 20,
    gate1HitRate: 85.3, hallucinationRetries: 8, factCheckFails: 5,
    avgConfidence: 0.88
  },
  knowledgeGaps: [
    { topic: '文件操作', count: 6, sampleQuery: 'fseek怎么用' },
    { topic: '链表', count: 4, sampleQuery: '链表头插法' },
    { topic: '内存管理', count: 3, sampleQuery: '内存泄漏怎么排查' },
    { topic: '结构体', count: 2, sampleQuery: '结构体赋值' },
    { topic: '位运算', count: 2, sampleQuery: '左移右移' },
  ],
  gapSuggestions: [
    { topic: '文件操作', urgency: '高', suggestion: '文件读写是C语言重要知识点。fopen/fclose/fread/fwrite/fseek等函数的使用。建议补充文件打开模式、文件指针操作、错误处理等内容。' },
    { topic: '链表', urgency: '中', suggestion: '链表是动态数据结构的核心。头插法、尾插法、删除节点、遍历打印是基本操作。建议补充链表节点的定义、动态内存分配和指针操作。' },
    { topic: '内存管理', urgency: '中', suggestion: 'C语言内存管理是实践难点。malloc/free配对使用、内存泄漏检测、悬垂指针防范是关键。建议补充常见的内存错误案例分析。' },
  ],
  hallucinationIncidents: [
    { query: 'malloc返回值类型', retryNum: 1, score: 0.72, issues: '混淆了void*和int*的强制转换', time: '2026-06-30T10:30:47', beforeContent: 'malloc返回void*需要强制转换...', afterContent: 'malloc返回void*，在C语言中不需要强制转换...' },
    { query: '数组名和指针的区别', retryNum: 2, score: 0.65, issues: '将数组名等同于指针变量', time: '2026-06-30T09:15:22', beforeContent: '数组名就是指向首元素的指针...', afterContent: '数组名是地址常量，不是指针变量，不能自增自减...' },
  ],
  recentEvents: [
    { type: 'GATE1_PASS', time: '2026-06-30T10:30:45', query: '什么是指针？', fragmentCount: 12, confidence: 0.92, detail: '命中12条片段' },
    { type: 'GATE1_PASS', time: '2026-06-30T10:28:12', query: 'malloc和calloc的区别', fragmentCount: 8, confidence: 0.88, detail: '命中8条片段' },
    { type: 'GATE1_REJECT', time: '2026-06-30T10:26:33', query: 'fseek函数参数', fragmentCount: 0, confidence: -1, detail: '当前知识库无相关知识点' },
    { type: 'PIPELINE_DONE', time: '2026-06-30T10:30:49', query: '什么是指针？', fragmentCount: 12, confidence: 0.89, detail: 'taskType=summary, success=true' },
    { type: 'HALLUCINATION_RETRY', time: '2026-06-30T10:30:47', query: 'malloc返回值类型', fragmentCount: 5, confidence: 0.72, detail: '第1次重试: 混淆了void*和int*的强制转换' },
  ]
}

// 知识库演示数据
export const demoKbDocuments = [
  { id: 1, kbId: 1, name: "C语言多级指针深潜.pdf", fileType: "pdf", status: "vectorized", tags: "指针,内存", createdAt: "2026-06-30T10:00:00", size: "1.4 MB", chunks: 24, model: "bge-large-zh", isDemo: true },
  { id: 2, kbId: 1, name: "malloc与动态内存管理.md", fileType: "md", status: "vectorized", tags: "内存管理,malloc", createdAt: "2026-06-29T14:30:00", size: "640 KB", chunks: 12, model: "bge-large-zh", isDemo: true },
  { id: 3, kbId: 1, name: "结构体与动态数据结构.txt", fileType: "txt", status: "pending", tags: "结构体,链表", createdAt: "2026-06-28T09:15:00", size: "850 KB", chunks: 0, model: "m3e-base", isDemo: true },
]
