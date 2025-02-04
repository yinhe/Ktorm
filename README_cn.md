<p align="center">
    <img src="docs/source/images/logo-full.png" alt="Ktorm" width="300" />
</p>
<p align="center">
    <a href="https://www.travis-ci.org/vincentlauvlwj/Ktorm">
        <img src="https://www.travis-ci.org/vincentlauvlwj/Ktorm.svg?branch=master" alt="Build Status" />
    </a>
    <a href="https://search.maven.org/search?q=g:%22me.liuwj.ktorm%22">
        <img src="https://img.shields.io/maven-central/v/me.liuwj.ktorm/ktorm-core.svg?label=Maven%20Central" alt="Maven Central" />
    </a>
    <a href="LICENSE">
        <img src="https://img.shields.io/badge/license-Apache%202-blue.svg?maxAge=2592000" alt="Apache License 2" />
    </a>
    <a href="https://app.codacy.com/app/vincentlauvlwj/Ktorm?utm_source=github.com&utm_medium=referral&utm_content=vincentlauvlwj/Ktorm&utm_campaign=Badge_Grade_Dashboard">
        <img src="https://api.codacy.com/project/badge/Grade/65d4931bfbe14fe986e1267b572bed53" alt="Codacy Badge" />
    </a>
    <a href="https://github.com/KotlinBy/awesome-kotlin">
        <img src="https://kotlin.link/awesome-kotlin.svg" alt="Awesome Kotlin Badge" />
    </a>
</p>


# Ktorm 是什么？

Ktorm 是直接基于纯 JDBC 编写的高效简洁的轻量级 Kotlin ORM 框架，它提供了强类型而且灵活的 SQL DSL 和方便的序列 API，以减少我们操作数据库的重复劳动。当然，所有的 SQL 都是自动生成的。Ktorm 基于 Apache 2.0 协议开放源代码，如果对你有帮助的话，请留下你的 star。

查看更多详细文档，请前往官网：[https://ktorm.liuwj.me](https://ktorm.liuwj.me/zh-cn)。

:cn: 简体中文 | :us: [English](README.md)

# 特性

 - 没有配置文件、没有 xml、没有第三方依赖、轻量级、简洁易用
 - 强类型 SQL DSL，将低级 bug 暴露在编译期
 - 灵活的查询，随心所欲地精确控制所生成的 SQL
 - 实体序列 API，使用 `filter`、`map`、`sortedBy` 等序列函数进行查询，就像使用 Kotlin 中的原生集合一样方便
 - 易扩展的设计，可以灵活编写扩展，支持更多操作符、数据类型、 SQL 函数、数据库方言等

<p align="center">
    <img src="docs/source/images/ktorm-example.jpg">
</p>

# 快速开始

Ktorm 已经发布到 maven 中央仓库和 jcenter，因此，如果你使用 maven 的话，只需要在 `pom.xml` 文件里面添加一个依赖： 

````xml
<dependency>
    <groupId>me.liuwj.ktorm</groupId>
    <artifactId>ktorm-core</artifactId>
    <version>${ktorm.version}</version>
</dependency>
````

或者 gradle： 

````groovy
compile "me.liuwj.ktorm:ktorm-core:${ktorm.version}"
````

首先，创建 Kotlin object，[描述你的表结构](https://ktorm.liuwj.me/zh-cn/schema-definition.html)： 

````kotlin
object Departments : Table<Nothing>("t_department") {
    val id by int("id").primaryKey()
    val name by varchar("name")
    val location by varchar("location")
}

object Employees : Table<Nothing>("t_employee") {
    val id by int("id").primaryKey()
    val name by varchar("name")
    val job by varchar("job")
    val managerId by int("manager_id")
    val hireDate by date("hire_date")
    val salary by long("salary")
    val departmentId by int("department_id")
}
````

然后，连接到数据库，执行一个简单的查询：

````kotlin
fun main() {
    Database.connect("jdbc:mysql://localhost:3306/ktorm", driver = "com.mysql.jdbc.Driver")

    for (row in Employees.select()) {
        println(row[Employees.name])
    }
}
````

现在，你可以执行这个程序了，Ktorm 会生成一条 SQL `select * from t_employee`，查询表中所有的员工记录，然后打印出他们的名字。 因为 `select` 函数返回的查询对象实现了 `Iterable<T>` 接口，所以你可以在这里使用 for-each 循环语法。当然，任何针对 `Iteralble<T>` 的扩展函数也都可用，比如 Kotlin 标准库提供的 map/filter/reduce 系列函数。

## SQL DSL

让我们在上面的查询里再增加一点筛选条件： 

```kotlin
val names = Employees
    .select(Employees.name)
    .where { (Employees.departmentId eq 1) and (Employees.name like "%vince%") }
    .map { row -> row[Employees.name] }
println(names)
```

生成的 SQL 如下: 

```sql
select t_employee.name as t_employee_name 
from t_employee 
where (t_employee.department_id = ?) and (t_employee.name like ?) 
```

这就是 Kotlin 的魔法，使用 Ktorm 写查询十分地简单和自然，所生成的 SQL 几乎和 Kotlin 代码一一对应。并且，Ktorm 是强类型的，编译器会在你的代码运行之前对它进行检查，IDE 也能对你的代码进行智能提示和自动补全。

基于条件的动态查询：

```kotlin
val names = Employees
    .select(Employees.name)
    .whereWithConditions {
        if (someCondition) {
            it += Employees.managerId.isNull()
        }
        if (otherCondition) {
            it += Employees.departmentId eq 1
        }
    }
    .map { it.getString(1) }
```

聚合查询：

```kotlin
val t = Employees
val salaries = t
    .select(t.departmentId, avg(t.salary))
    .groupBy(t.departmentId)
    .having { avg(t.salary) greater 100.0 }
    .associate { it.getInt(1) to it.getDouble(2) }
```

Union：

```kotlin
Employees
    .select(Employees.id)
    .unionAll(
        Departments.select(Departments.id)
    )
    .unionAll(
        Departments.select(Departments.id)
    )
    .orderBy(Employees.id.desc())
```

多表连接查询：

```kotlin
data class Names(val name: String, val managerName: String?, val departmentName: String)

val emp = Employees.aliased("emp")
val mgr = Employees.aliased("mgr")
val dept = Departments.aliased("dept")

val results = emp
    .leftJoin(dept, on = emp.departmentId eq dept.id)
    .leftJoin(mgr, on = emp.managerId eq mgr.id)
    .select(emp.name, mgr.name, dept.name)
    .orderBy(emp.id.asc())
    .map {
        Names(
            name = it.getString(1),
            managerName = it.getString(2),
            departmentName = it.getString(3)
        )
    }
```

插入：

```kotlin
Employees.insert {
    it.name to "jerry"
    it.job to "trainee"
    it.managerId to 1
    it.hireDate to LocalDate.now()
    it.salary to 50
    it.departmentId to 1
}
```

更新：

```kotlin
Employees.update {
    it.job to "engineer"
    it.managerId to null
    it.salary to 100

    where {
        it.id eq 2
    }
}
```

删除：

```kotlin
Employees.delete { it.id eq 4 }
```

更多 SQL DSL 的用法，请参考[具体文档](https://ktorm.liuwj.me/zh-cn/query.html)。

## 实体类与列绑定

除了 SQL DSL 以外，Ktorm 也支持实体对象。首先，我们需要定义实体类，然后在表对象中使用 `bindTo` 函数将表与实体类进行绑定。在 Ktorm 里面，我们使用接口定义实体类，继承 `Entity<E>` 即可：

```kotlin
interface Department : Entity<Department> {
    val id: Int
    var name: String
    var location: String
}

interface Employee : Entity<Employee> {
    val id: Int?
    var name: String
    var job: String
    var manager: Employee?
    var hireDate: LocalDate
    var salary: Long
    var department: Department
}
```

修改前面的表对象，把数据库中的列绑定到实体类的属性上：

```kotlin
object Departments : Table<Department>("t_department") {
    val id by int("id").primaryKey().bindTo { it.id }
    val name by varchar("name").bindTo { it.name }
    val location by varchar("location").bindTo { it.location }
}

object Employees : Table<Employee>("t_employee") {
    val id by int("id").primaryKey().bindTo { it.id }
    val name by varchar("name").bindTo { it.name }
    val job by varchar("job").bindTo { it.job }
    val managerId by int("manager_id").bindTo { it.manager.id }
    val hireDate by date("hire_date").bindTo { it.hireDate }
    val salary by long("salary").bindTo { it.salary }
    val departmentId by int("department_id").references(Departments) { it.department }
}
```

> 命名规约：强烈建议使用单数名词命名实体类，使用名词的复数形式命名表对象，如：Employee/Employees、Department/Departments。

完成列绑定后，我们就可以使用针对实体类的各种方便的扩展函数。比如根据名字获取 Employee 对象： 

```kotlin
val vince = Employees.findOne { it.name eq "vince" }
println(vince)
```

`findOne` 函数接受一个 lambda 表达式作为参数，使用该 lambda 的返回值作为条件，生成一条查询 SQL，自动 left jion 了关联表 `t_department`。生成的 SQL 如下：

```sql
select * 
from t_employee 
left join t_department _ref0 on t_employee.department_id = _ref0.id 
where t_employee.name = ?
```

其他 `find*` 系列函数：

```kotlin
Employees.findAll()
Employees.findById(1)
Employees.findListByIds(listOf(1))
Employees.findMapByIds(listOf(1))
Employees.findList { it.departmentId eq 1 }
Employees.findOne { it.name eq "vince" }
```

将实体对象保存到数据库：

```kotlin
val employee = Employee {
    name = "jerry"
    job = "trainee"
    manager = Employees.findOne { it.name eq "vince" }
    hireDate = LocalDate.now()
    salary = 50
    department = Departments.findOne { it.name eq "tech" }
}

Employees.add(employee)
```

将内存中实体对象的变化更新到数据库：

```kotlin
val employee = Employees.findById(2) ?: return
employee.job = "engineer"
employee.salary = 100
employee.flushChanges()
```

从数据库中删除实体对象：

```kotlin
val employee = Employees.findById(2) ?: return
employee.delete()
```

更多实体 API 的用法，可参考[列绑定](https://ktorm.liuwj.me/zh-cn/entities-and-column-binding.html)和[实体查询](https://ktorm.liuwj.me/zh-cn/entity-finding.html)相关的文档。

## 实体序列 API

除了 `find*` 函数以外，Ktorm 还提供了一套名为”实体序列”的 API，用来从数据库中获取实体对象。正如其名字所示，它的风格和使用方式与 Kotlin 标准库中的序列 API 及其类似，它提供了许多同名的扩展函数，比如 `filter`、`map`、`reduce` 等。

要获取一个实体序列，我们可以在表对象上调用 `asSequence` 扩展函数：

```kotlin
val sequence = Employees.asSequence()
```

Ktorm 的实体序列 API，大部分都是以扩展函数的方式提供的，这些扩展函数大致可以分为两类，它们分别是中间操作和终止操作。

### 中间操作

这类操作并不会执行序列中的查询，而是修改并创建一个新的序列对象，比如 `filter` 函数会使用指定的筛选条件创建一个新的序列对象。下面使用 `filter` 获取部门 1 中的所有员工：

```kotlin
val employees = Employees.asSequence().filter { it.departmentId eq 1 }.toList()
```

可以看到，用法几乎与 `kotlin.sequences.Sequence` 完全一样，不同的仅仅是在 lambda 表达式中的等号 `==` 被这里的 `eq` 函数代替了而已。`filter` 函数还可以连续使用，此时所有的筛选条件将使用 `and` 操作符进行连接，比如：

```kotlin
val employees = Employees
    .asSequence()
    .filter { it.departmentId eq 1 }
    .filter { it.managerId.isNotNull() }
    .toList()
```

生成 SQL：

```sql
select * 
from t_employee 
left join t_department _ref0 on t_employee.department_id = _ref0.id 
where (t_employee.department_id = ?) and (t_employee.manager_id is not null)
```

使用 `sortedBy` 或 `sortedByDescending` 对序列中的元素进行排序：

```kotlin
val employees = Employees.asSequence().sortedBy { it.salary }.toList()
```

使用 `drop` 和 `take` 函数进行分页：

````kotlin
val employees = Employees.asSequence().drop(1).take(1).toList()
````

### 终止操作

实体序列的终止操作会马上执行一个查询，获取查询的执行结果，然后执行一定的计算。for-each 循环就是一个典型的终止操作，下面我们使用 for-each 循环打印出序列中所有的员工：

```kotlin
for (employee in Employees.asSequence()) {
    println(employee)
}
```

生成的 SQL 如下：

```sql
select * 
from t_employee 
left join t_department _ref0 on t_employee.department_id = _ref0.id
```

`toCollection`、`toList` 等方法用于将序列中的元素保存为一个集合：

````kotlin
val employees = Employees.asSequence().toCollection(ArrayList())
````

`mapColumns` 函数用于获取指定列的结果：

````kotlin
val names = Employees.asSequenceWithoutReferences().mapColumns { it.name }
````

除此之外，还有 `mapColumns2`、`mapColumns3` 等更多函数，它们用来同时获取多个列的结果，这时我们需要在闭包中使用 `Pair` 或 `Triple` 包装我们的这些字段，函数的返回值也相应变成了 `List<Pair<C1?, C2?>>` 或 `List<Triple<C1?, C2?, C3?>>`：

```kotlin
Employees
    .asSequenceWithoutReferences()
    .filter { it.departmentId eq 1 }
    .mapColumns2 { Pair(it.id, it.name) }
    .forEach { (id, name) ->
        println("$id:$name")
    }
```

生成 SQL：

```sql
select t_employee.id, t_employee.name
from t_employee 
where t_employee.department_id = ?
```

其他我们熟悉的序列函数也都支持，比如 `fold`、`reduce`、`forEach` 等，下面使用 `fold` 计算所有员工的工资总和：

````kotlin
val totalSalary = Employees.asSequence().fold(0L) { acc, employee -> acc + employee.salary }
````

### 序列聚合

实体序列 API 不仅可以让我们使用类似 `kotlin.sequences.Sequence` 的方式获取数据库中的实体对象，它还支持丰富的聚合功能，让我们可以方便地对指定字段进行计数、求和、求平均值等操作。

下面使用 `aggregateColumns` 函数获取部门 1 中工资的最大值：

```kotlin
val max = Employees
    .asSequenceWithoutReferences()
    .filter { it.departmentId eq 1 }
    .aggregateColumns { max(it.salary) }
```

如果你希望同时获取多个聚合结果，可以改用 `aggregateColumns2` 或 `aggregateColumns3` 函数，这时我们需要在闭包中使用 `Pair` 或 `Triple` 包装我们的这些聚合表达式，函数的返回值也相应变成了 `Pair<C1?, C2?>` 或 `Triple<C1?, C2?, C3?>`。下面的例子获取部门 1 中工资的平均值和极差：

```kotlin
val (avg, diff) = Employees
    .asSequenceWithoutReferences()
    .filter { it.departmentId eq 1 }
    .aggregateColumns2 { Pair(avg(it.salary), max(it.salary) - min(it.salary)) }
```

生成 SQL：

```sql
select avg(t_employee.salary), max(t_employee.salary) - min(t_employee.salary) 
from t_employee 
where t_employee.department_id = ?
```

除了直接使用 `aggregateColumns` 函数以外，Ktorm 还为序列提供了许多方便的辅助函数，他们都是基于 `aggregateColumns` 函数实现的，分别是 `count`、`any`、`none`、`all`、`sumBy`、`maxBy`、`minBy`、`averageBy`。

下面改用 `maxBy` 函数获取部门 1 中工资的最大值：

````kotlin
val max = Employees
    .asSequenceWithoutReferences()
    .filter { it.departmentId eq 1 }
    .maxBy { it.salary }
````

除此之外，Ktorm 还支持分组聚合，只需要先调用 `groupingBy`，再调用 `aggregateColumns`。下面的代码可以获取所有部门的平均工资，它的返回值类型是 `Map<Int?, Double?>`，其中键为部门 ID，值是各个部门工资的平均值：

````kotlin
val averageSalaries = Employees
    .asSequenceWithoutReferences()
    .groupingBy { it.departmentId }
    .aggregateColumns { avg(it.salary) }
````

生成 SQL：

```sql
select t_employee.department_id, avg(t_employee.salary) 
from t_employee 
group by t_employee.department_id
```

在分组聚合时，Ktorm 也提供了许多方便的辅助函数，它们是 `eachCount(To)`、`eachSumBy(To)`、`eachMaxBy(To)`、`eachMinBy(To)`、`eachAverageBy(To)`。有了这些辅助函数，上面获取所有部门平均工资的代码就可以改写成：

```kotlin
val averageSalaries = Employees
    .asSequenceWithoutReferences()
    .groupingBy { it.departmentId }
    .eachAverageBy { it.salary }
```

除此之外，Ktorm 还提供了 `aggregate`、`fold`、`reduce` 等函数，它们与 `kotlin.collections.Grouping` 的相应函数同名，功能也完全一样。下面的代码使用 `fold` 函数计算每个部门工资的总和：

```kotlin
val totalSalaries = Employees
    .asSequenceWithoutReferences()
    .groupingBy { it.departmentId }
    .fold(0L) { acc, employee -> 
        acc + employee.salary 
    }
```

更多实体序列 API 的用法，可参考[实体序列](https://ktorm.liuwj.me/zh-cn/entity-sequence.html)和[序列聚合](https://ktorm.liuwj.me/zh-cn/sequence-aggregation.html)相关的文档。