# Магия Spring Framework своими руками


**DISCLAIMER**

> Примеры кода в статье будут намеренно упрощены в угоду компактности изложения идеи, сама демонстрация идеи не страдает. Более пригодный для промышленной эксплуатации код можно найти в конце статьи и в [репозитории с практикой](https://github.com/neshkeev/spring-proxy-demo).

# TLDR

Прокси объекты являются основой "магии" Spring Framework. В качестве демонстрации реализована `@JmxExporter` аннотация, которая позволяет превратить любой Spring Bean в JMX MBean.
![](https://habrastorage.org/webt/ad/ce/ik/adceikkdmp319mk8mw3j5o5jank.jpeg)

# Мотивация

Spring Framework позволяет сфокусироваться на бизнес-логике, а вся настройка инфраструктуры выполняется силами самого фреймворка. Так, например, разработчик вешает аннотацию `@RestController` на бин, и бин начинает обрабатывать REST запросы, при этом разработчик не трогает Servlet Context, не настраивает цепочку фильтров или веб сервер: все конфигурируется автоматически. Автоматическая настройка инфраструктуры выполняется благодаря постобработке бинов. Зачастую для реализации дополнительной функциональности применяется Spring AOP - аспектно-ориентированное программирование.

Spring AOP удобен, когда необходимо выполнить код перед, после или вместо вызова метода бина. Управление транзакциями - классический пример использования Spring AOP: начать транзакцию перед вызовом метода и зафиксировать ее после завершения метода.

Spring AOP бин, который накручивает дополнительную функциональность другим Spring бинам, помечается аннотацией `@Aspect`, но как потом этот бин используется для добавления дополнительной функциональности в поток исполнения? Все работает благодаря тому, что в жизненный цикл бина можно вклиниться при помощи `BeanPostProcessor`.

# Прокси объекты

## Наследование

Прокси объект полностью повторяет интерфейс проксируемого объекта, но в то же время может исполнять дополнительный код. Предельным случаем прокси объекта можно считать наследование: по согласно принципу подстановки Барбары Лисков объект наследник можно использовать везде, где можно использовать объект родитель. При этом в наследниках можно переопределять методы родителя, дополняя или полностью изменяя логику.

## Proxy объекты в JDK

Другим способом создать прокси объект является класс `java.lang.reflect.Proxy`, который появился в Java 1.3. При помощи `Proxy` можно динамически создавать объекты, которые реализуют некоторый интерфейс. Так, программист не обязан предоставлять реализацию интерфейса: она может появиться в рантайме. Для создания прокси-объекта необходимо:

- интерфейс, т.к. `java.lang.reflect.Proxy` не умеет работать с конкретными или абстрактными классами;
- обработчик, который реализует интерфейс `java.lang.reflect.InvocationHandler`;
- реализация метода `java.lang.reflect.InvocationHandler#invoke` для перехвата вызова методов оригинального интерфейса.

### Пример "Hello, World!"

В качестве первого примера предлагается рассмотреть прокси объект, которым является строка `"Hello, World!"`. Особенности:

- строки в java являются объектами класса `java.lang.String`, что не подходит для `Proxy`, т.к. нужен интерфейс, поэтому прокси будет создаваться для интерфейса `java.lang.CharSequence`;
- при печати строки неявно вызывается метод `toString`, следовательно именно этот метод необходимо проксировать;
- вызов остальных методов будет делегирован строковой константе `"Hello, World!"`.

1. Реализация `java.lang.reflect.InvocationHandler`:

```java
public class HelloWorldInvocationHandler implements InvocationHandler {
    public static final String HELLO_WORLD_MESSAGE = "Hello, World!";

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
        if ("toString".equals(method.getName())) {
            return HELLO_WORLD_MESSAGE;
        }
        return method.invoke(HELLO_WORLD_MESSAGE, args);
    }

}
```
Пояснения:
- метод `invoke` принимает на вход три параметра:
    - сам прокси объект, для которого вызван метод. Методы могут быть как из списка методов реализуемого интерфейса, так и методы `java.lang.Object`;
    - объект `java.lang.reflect.Method` указывает на вызванный метод;
    - список аргументов, которые были переданы в метод при вызове.
- метод `invoke` обрабатывает вызовы всех методов проксируемого объекта;
- активно используется Java Reflection API.

2. Создание прокси объекта:
```java
public CharSequence helloWorldProxy() {
    return (CharSequence) Proxy.newProxyInstance(
        HelloWorldInvocationHandler.class.getClassLoader(),
        new Class[]{CharSequence.class},
        new HelloWorldInvocationHandler());
}
```

Пояснения:

- Создание proxy объекта выполняется при помощи `Proxy#newProxyInstance`, аргументами которого являются:
    - загрузчик классов для загрузки создаваемого прокси объекта;
    - список интерфейсов, которые реализует прокси объект;
    - обработчик, в метод `invoke` которого будут направляться вызовы всех методов прокси объекта.
- Метод `Proxy#newProxyInstance` возвращает результат типа `java.lang.Object`, поэтому необходимо явно привести его тип к реализуемому интерфейсу.

### Пример "Генератор паролей"

Реализовывать можно также и пользовательские интерфейсы. В качестве примера будет реализован интерфейс `PasswordGenerator` с одним методом `getPassword`:

1. Определение интерфейса генератора паролей:
```java
public interface PasswordGenerator {
    String getPassword();
}
```

2. Реализация `java.lang.reflect.InvocationHandler` для генератора паролей:

```java
public class PasswordGeneratorInvocationHandler implements InvocationHandler {
    private final String password;

    public PasswordGeneratorInvocationHandler(int size) {
        this.password = generatePassword(size);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("getPassword".equals(method.getName())) return password;

        return method.invoke(password, args);
    }

    private static String generatePassword(int size) {
        // реализация опущена
    }

}
```

Пояснения:

- `PasswordGeneratorInvocationHandler` может генерировать пароли указанной длины, которая задается в конструкторе;
- пароль генерируется во время создания объекта `PasswordGeneratorInvocationHandler` при помощи вспомогательного метода `generatePassword`;
- реализация `generatePassword` опущена в целях экономии места, код можно посмотреть в [репозитории](https://github.com/neshkeev/spring-proxy-demo/blob/master/src/main/java/com/github/neshkeev/spring/proxy/simple/PasswordGeneratorInvocationHandler.java#L25);
- метод `PasswordGeneratorInvocationHandler#invoke` проверяет какой метод был вызван:
    - если был вызван метод `getPassword`, то вернуть заготовленный пароль из поля `password`;
    - в противном случае делегировать вызов метода заготовленному объекту `password`.
- метод `getPassword`, вызванный на одном объекте на базе `PasswordGeneratorInvocationHandler`, будет возвращать всегда одно и то же значение.

3. Создание прокси объекта:

```java
public PasswordGenerator passwordGenerator() {
    return (PasswordGenerator) Proxy.newProxyInstance(
        PasswordGeneratorInvocationHandler.class.getClassLoader(),
        new Class[]{PasswordGenerator.class},
        new PasswordGeneratorInvocationHandler(32));
}
```

Создание прокси объекта аналогично созданию прокси объекта из предыдущего раздела.

<details>
<summary>Задание</summary>

Как было отмечено ранее, один прокси объект `PasswordGenerator` будет возвращать всегда одно и то же значение пароля вне зависимости от того, сколько раз был вызван `generatePassword`. В целях безопасности лучше показывать пароль только один раз. Как изменить `PasswordGeneratorInvocationHandler`, чтобы метод `getPassword` для одного прокси объекта возвращал сгенерированный пароль только один раз, а при последующих вызовах на том же самом прокси генерировал исключение `IllegalStateException("Password has already been shown")`?
</details>

### Прокси как обёртка

Прокси объекты могут выступать обёртками над реальными объектами. Такой способ может быть полезным в случаях, когда:

- нет возможности изменить реализацию метода, а наследование не годится (например, для `final` классов);
- необходимо отделить бизнес-логику от сервисного кода (логирование, метрики), что очень похоже на аспектно-ориентированное программирование.

Для реализации обёртки необходимо:

- в конструктор обработчика прокси объектов передать готовый объект,
- в методе `invoke` делегировать все вызовы готовому объекту,
- выполнить сервисный код перед или после вызова метода.

В качестве примера можно рассмотреть логирование методов произвольного объекта, тогда:

1. реализация `InvocationHandler` для обертки:

```java
public class LoggerWrapperInvocationHandler<T> implements InvocationHandler {
    private final static Logger LOG = LoggerFactory.getLogger(LoggerWrapperInvocationHandler.class);

    private final T delegate;

    public LoggerWrapperInvocationHandler(T delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        LOG.info("Start executing {}", method.getName());
        final var start = LocalDateTime.now();
        try {
            return method.invoke(delegate, args);
        }
        finally {
            final var end = LocalDateTime.now();
            LOG.info("End executing {} which took {}ns",
                method.getName(),
                Duration.between(start, end).toNanos());
        }
    }
}
```
Пояснения:

- класс `LoggerWrapperInvocationHandler` параметризован типом обёртываемого объекта,
- метод `invoke` делегирует вызов каждого метода объекту `delegate`,
- перед вызовом любого метода фиксируется факт начала исполнения метода, а также запускается таймер,
- после завершения метода фиксируется информация о времени, затраченном на исполнение метода.

2. создание прокси объекта:

```java
public<K, V> Map<K, V> wrappedMap(Map<K, V> map) {
    //noinspection unchecked
    return (Map<K, V>) Proxy.newProxyInstance(
        LoggerWrapperInvocationHandler.class.getClassLoader(),
        new Class[]{Map.class},
        new LoggerWrapperInvocationHandler<>(map));
```

Код создания прокси объекта идентичен коду из предыдущих разделов, основные отличия:

- метод `wrappedMap` принимает в качестве аргумента произвольный объект `map` типа `Map<K, V>`;
- прокси объект реализует интерфейс `Map`;
- объект `map` передается в конструктор `LoggerWrapperInvocationHandler`.

# Прокси объекты в Spring Framework

Spring полагается на проксирование объектов как на основной способ реализации служебного кода, в качестве примеров можно выделить:

- работа с базой данных через `@Repository`: программист описывает в своем коде интерфейс для работы с базой данных, а Spring генерирует типовую реализацию методов интерфейса;
- управление транзакциями через `@Transactional`: программист добавляет аннотацию `@Transactional` к своим методам, а Spring создает обёртки вокруг них, где стартует и фиксирует транзакции;
- обработка REST API через `@RestController`: программист добавляет аннотацию `@RestController` к своему классу, а также указывает какие методы класса какие запросы обрабатывают, а Spring поднимает `ServletContext`;
- чтение из Kafka через `@KafkaListener`, работа с потоками через `@Async`, работа с задачами по расписанию `@Scheduled` и многое другое - всё работает через прокси объекты.

Замечание:

> Внутри Spring использует по умолчанию [CGlib](https://github.com/cglib/cglib) в качестве библиотеки для создания прокси объектов, что позволяет создавать прокси объекты даже для классов, а не только для интерфейсов как в `java.lang.reflect.Proxy`.
Использование cglib избавило программистов от необходимости объявлять интерфейсы к Spring бинам.
Рассмотрение возможностей библиотеки cglib выходит за пределы статьи, поэтому проксирование будет выполняться при помощи класса `Proxy` из стандартной поставки JDK, что в любом случае позволит продемонстрировать идею.

## Точки расширения

Любой класс может стать Spring бином, если добавить к нему необходимые аннотации. По умолчанию Spring создает по одному экземпляру каждого бина на протяжении жизни приложения.

Spring Framework стратегически расположил несколько точек расширения, в которых программист может выполнить свой код. Код может быть определен в самом классе бина, для этого необходимо на методы класса повесить аннотации:

- `@PostConstruct` - выполнить код после того, как бин создан;
- `@PreDestroy` - выполнить код перед тем, как приложение завершит свою работу, а бин будет уничтожен.

## BeanPostProcessor

Подход с выполнением кода напрямую в бине можно рассматривать как локальную точку расширения. Локальная точка расширения не годится, когда необходимо выполнить один и тот же код для всех бинов. В этом случае в дело вступает `org.springframework.beans.factory.config.BeanPostProcessor` - глобальная точка расширения.

Бин `BeanPostProcessor` пропускает через себя все остальные Spring бины и позволяет выполнить один и тот же код для них. При этом можно фильтровать бины по метаданным, записанными в виде аннотаций. Например, можно собрать все `@RestConroller` бины и создать для них сервлеты, или собрать все `@KafkaListener` бины и зарегистрировать их как Kafka консьюмеры. Объектов типа `BeanPostProcessor` может быть много, каждый из которых выполняет свою работу, и в этом заключается "магия" Spring Framework.

## Пример

В качестве примера использования `BeanPostProcessor` предлагается рассмотреть автоматическую конфигурацию JMX MBean объектов для каждого бина, помеченного аннотацией `@JmxExporter`:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JmxExporter {
    String name() default "";
}
```

### Java Management Extensions (JMX)

Java Management Extensions (JMX) - это технология, которая позволяет управлять java приложением в реальном времени. Технология JMX вводит понятие MBean, что представляет собой объект, который может вызывать код приложения. Все MBean бины регистрируются на сервере, к которому можно подключиться извне и выполнить код приложения через один из зарегистрированных MBean бинов.

Самым большим ограничением работы с MBean бинами является список доступных типов:

- примитивы (`int`, `boolean`, `char` и т.д.),
- обёртки над примитивами (`Integer`, `Boolean`, `Character` и т.д.),
- `java.lang.String`
- `byte[]`.

Для обхода этого ограничения при работе с более сложными типами можно сериализовать объекты в `json` и обратно, что потребует дополнительной работы во время регистрации и вызова MBean объектов.

Самый простой JMX клиент - это jconsole, который входит в стандартную поставку JDK.

### Регистрация MBean

Любой Spring бин, который помечен аннотацией `@JmxExporter` должен выставить соответствующий ему `MBean`, т.е. необходимо выполнить одинаковый код для неизвестного числа бинов, а значит необходимо реализовать `BeanPostProcessor`:

```java
public class JmxExporterPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        registerMBean(bean, beanName);
        return bean;
    }

    private void registerMBean(Object bean, String beanName) {
        final Class<?> aClass = bean.getClass();
        final var annotation = aClass.getAnnotation(JmxExporter.class);
        if (annotation == null) return;

        try {
            final var name = StringUtils.hasText(annotation.name())
                    ? annotation.name()
                    : beanName;

            final var objectName = new ObjectName(aClass.getPackageName() + ":type=basic,name=" + name);

            final var platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
            final var proxy = getDynamicMBean(bean);
            platformMBeanServer.registerMBean(proxy, objectName);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DynamicMBean getDynamicMBean(Object bean) {
        // реализация приведена ниже
        return null;
    }
}
```

Пояснения:

- каждый Spring бин после создания пройдет по всем `BeanPostProcessor` объектам и будет передан в метод `postProcessAfterInitialization`;
- регистрация `MBean` объекта для бина выполняется в `registerMBean` и вынесена в отдельный метод, чтобы повысить уровень читаемости кода;
- Spring бины без аннотации `@JmxExporter` пропускаются;
- если имя не указано через аннотацию `@JmxExporter`, то имя Spring бина будет использоваться в качестве имени объекта `MBean`; 
- метод `ManagementFactory#getPlatformMBeanServer` возвращает текущий объект `MBeanServer`, который является singleton объектом;
- объект `MBean` реализует интерфейс `javax.management.DynamicMBean` и создается в методе `getDynamicMBean`;

### Создание MBean

Объект `MBean` реализует интерфейс `DynamicMBean`, а значит является кандидатом для создания прокси объекта. Создание объекта `MBean` выполняется в методе `JmxExporterPostProcessor#getDynamicMBean`:

```java
private DynamicMBean getDynamicMBean(Object bean) {
    return (DynamicMBean) Proxy.newProxyInstance(
            JmxExporterPostProcessor.class.getClassLoader(),
            new Class[]{DynamicMBean.class},
            new JmxWrapperInvocationHandler(bean));
}
```

Создание `MBean` объекта аналогично созданию прокси объекта из предыдущих разделов, отличием является тот факт, что Spring бин передается в `JmxWrapperInvocationHandler`, где бин используется в качестве делегата для вызова методов на прокси объекте.

### InvocationHandler для MBean прокси объектов

Реализация `InvocationHandler` для `MBean` бинов:

```java
public class JmxWrapperInvocationHandler implements InvocationHandler {

    private final Object bean;

    public JmxWrapperInvocationHandler(Object bean) {
        this.bean = bean;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "getAttribute", "setAttribute", "getAttributes", "setAttributes" -> null;
            case "getMBeanInfo" ->
                    new MBeanInfo(bean.getClass().getName(),
                            bean.getClass().getName(),
                            new MBeanAttributeInfo[0],
                            new MBeanConstructorInfo[0],
                            // получить публичные методы
                            MBeanUtils.operations(bean.getClass()),
                            new MBeanNotificationInfo[0]);
            case "invoke" -> invokeProxy(args);
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    private Object invokeProxy(Object[] args) throws Exception {
        final var actionName = (String) args[0];
        final var params = (Object[]) args[1];
        
        final Class<?>[] paramTypes = Arrays.stream(params)
                .map(Object::getClass)
                .toArray(Class<?>[]::new);
        
        final var declaredMethod = bean.getClass().getDeclaredMethod(actionName, paramTypes);
        return declaredMethod.invoke(bean, params);
    }
}
```

Пояснения:

- Реализация `InvocationHandler` для `MBean` бинов аналогична реализации обработчика для обёртки над готовым объектом;
- интерфейс `javax.management.DynamicMBean` содержит шесть методов, но только два из них имеют нетривиальную обработку:
    - `getMBeanInfo` возвращает объект `MBeanInfo` - метаданные `MBean` бина, в том числе и список доступных операций (каждая операция имеет метод в классе переданного Spring бина);
    - `invoke` вызывает одну из доступных операций.
- метод `invokeProxy` обрабатывает вызов операции;
- поиск метода Spring бина для вызова выполняется по имени операции и сигнатуре;
- сигнатура метода Spring бина вычисляется по массиву классов переданных аргументов.

Приведенная реализация `JmxWrapperInvocationHandler` намеренно упрощена в угоду компактности. Она не будет работать, если Spring бин выставляет методы, в сигнатуре которых есть типы, отличные от примитивных, `String` и `byte[]`. Более пригодную для промышленной эксплуатации версию можно найти в конце статьи или в [репозитории с практикой](https://github.com/neshkeev/spring-proxy-demo/blob/master/src/main/java/com/github/neshkeev/spring/proxy/jmx/JmxWrapperInvocationHandler.java).

### Активация JMX

Демонстрация конфигурации MBean бинов по Spring бинам будет выполняться на примере REST контроллера:

```java
@JmxExporter
@RestController
public class CustomerController {
    private final Map<Integer, Customer> customers = new HashMap<>();

    @PostMapping("/customers")
    public void add(@RequestBody Customer customer) {
        customers.put(customer.id(), customer);
    }

    @GetMapping("/customers/{id}")
    public Customer get(@PathVariable("id") int id) {
        return customers.get(id);
    }

    @GetMapping("/customers")
    public Collection<Customer> list() {
        return customers.values();
    }
}
```

Класс является типовым REST контроллером, поэтому дополнительные пояснения излишни. Стоит отметить лишь, что в дополнение к аннотации `@RestController` появилась аннотация `@JmxExporter`.

`Customer` - это `record` с тремя полями:
```java
public record Customer(int id, String name, boolean active) {
}
```

### Демонстрация работы

Запустив приложение, можно подключиться к нему по JMX и увидеть, что `CustomerController` зарегистрировался в качестве `MBean` бина, который имеет все три операции: `add`, `get`, `list`.

1. Добавить клиента через curl, получить через JMX:
```bash
curl http://localhost:8080/customers \
    -X POST \
    -H "Content-type: application/json" \
    -d '{"id":1,"name":"Hello, World!", "active": true}'
```

![JMX get operation](https://habrastorage.org/webt/x3/gf/mr/x3gfmrh0p0bwovimu-lnu3rxpbu.png)

2. Добавить клиента через JMX, получить через `curl`:
![JMX add operation](https://habrastorage.org/webt/k5/3w/x4/k53wx4uclnyvfkb6yp6kqeyii9u.png)
```bash
curl -s http://localhost:8080/customers/2 | json_pp
{
   "active" : true,
   "id" : 2,
   "name" : "Jmx Client!"
}
```
3. Получить список всех клиентов
```bash
curl -s http://localhost:8080/customers | json_pp
[
   {
      "active" : true,
      "id" : 1,
      "name" : "Hello, World!"
   },
   {
      "active" : true,
      "id" : 2,
      "name" : "Jmx Client!"
   }
]
```
![](https://habrastorage.org/webt/lz/iw/qj/lziwqjdvha-yhwmp3vz6tdmcezq.png)

# Заключение

В статье было продемонстрировано, как при помощи прокси `BeanPostProcessor` можно создать "магию" Spring своими руками. Полный проект можно найти в [репозитории с практикой](https://github.com/neshkeev/spring-proxy-demo), где так же добавлены тесты и параметры запуска. В дополнение можно запустить проект на Gitpod, виртуальной машине в облаке (50 часов в месяц бесплатно).

[![Gitpod](https://img.shields.io/badge/Open%20in%20Gitpod-908a85?logo=gitpod)](https://gitpod.io/#https://github.com/neshkeev/spring-proxy-demo)

# Дополнительно

Код в статье был намеренно упрощен в угоду компактности и фокусировке на основной теме, а поэтому если переносить его один-в-один, то не все может работать. Более полную версию можно получить в [репозитории с практикой](https://github.com/neshkeev/spring-proxy-demo). Ниже в спойлерах приведен полный код для историчности или на случай, если репозиторий на GitHub пропадет или станет недоступен.

<details>
  <summary>JmxExporterPostProcessor.java</summary>

```java
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.management.DynamicMBean;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;

@Component
public class JmxExporterPostProcessor implements BeanPostProcessor {
    private final MBeanInvocable mBeanInvocable;

    @Lazy
    public JmxExporterPostProcessor(MBeanInvocable mBeanInvocable) {
        this.mBeanInvocable = mBeanInvocable;
    }

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        registerMBean(bean, beanName);
        return bean;
    }

    private void registerMBean(Object bean, String beanName) {
        final Class<?> aClass = bean.getClass();
        final var annotation = aClass.getAnnotation(JmxExporter.class);
        if (annotation == null) return;

        try {
            final var name = StringUtils.hasText(annotation.name())
                    ? annotation.name()
                    : beanName;

            final var objectName = new ObjectName(aClass.getPackageName() + ":type=basic,name=" + name);

            final var platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
            final var proxy = getDynamicMBean(bean);
            platformMBeanServer.registerMBean(proxy, objectName);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DynamicMBean getDynamicMBean(Object bean) {
        return (DynamicMBean) Proxy.newProxyInstance(
                JmxExporterPostProcessor.class.getClassLoader(),
                new Class[]{DynamicMBean.class},
                new JmxWrapperInvocationHandler(mBeanInvocable, bean));
    }
}
```
</details>

<details>
  <summary>JmxWrapperInvocationHandler.java</summary>

```java
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class JmxWrapperInvocationHandler implements InvocationHandler {

    private final MBeanInvocable mBeanInvocable;

    private final Object bean;

    public JmxWrapperInvocationHandler(MBeanInvocable mBeanInvocable, Object bean) {
        this.mBeanInvocable = mBeanInvocable;
        this.bean = bean;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "getAttribute", "setAttribute", "getAttributes", "setAttributes" -> null;
            case "getMBeanInfo" ->
                    new MBeanInfo(bean.getClass().getName(),
                            bean.getClass().getName(),
                            new MBeanAttributeInfo[0],
                            new MBeanConstructorInfo[0],
                            MBeanUtils.operations(bean.getClass()),
                            new MBeanNotificationInfo[0]);
            case "invoke" -> invoke(args);
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    private Object invoke(Object[] args) throws Exception {
        final var actionName = (String) args[0];
        final var params = (Object[]) args[1];

        return mBeanInvocable.getResult(bean, actionName, params);
    }
}
```
</details>

<details>
  <summary>MBeanInvocable.java</summary>

```java
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.*;

@Service
public class MBeanInvocable {

    private final ObjectMapper objectMapper;

    public MBeanInvocable(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Object getResult(Object target, String methodName, Object[] params) throws ReflectiveOperationException, JsonProcessingException {
        var invocable = prepareMethod(target, methodName, params);
        var result = invocable.invoke(target);
        return augmentResult(result);
    }

    private record PreparedInvocable(Method method, Object[] args) {
        private Object invoke(Object target) throws ReflectiveOperationException {
            return method.invoke(target, args);
        }
    }

    private PreparedInvocable prepareMethod(Object target, String methodName, Object[] params) throws NoSuchMethodException, JsonProcessingException {
        final var args = new Object[params.length];
        final var candidates = getCandidateMethods(target, methodName);

        for (Method candidate : candidates) {
            if (candidate.getParameterCount() != params.length) continue;

            int i = 0;
            for (; i < candidate.getParameterCount(); i++) {
                final Object jmxParam = params[i];
                final var methodParam = candidate.getParameters()[i];

                if (jmxParam != null) {
                    args[i] = tryConvert(jmxParam, methodParam.getType());
                    if (args[i] == null) break;
                }
                else {
                    args[i] = null;
                }
            }

            if (i == candidate.getParameterCount()) {
                return new PreparedInvocable(candidate, args);
            }
        }
        throw new NoSuchMethodException("No method matches the required signature");
    }

    private Object tryConvert(Object jmxParam, Class<?> paramType) throws JsonProcessingException {
        final var primitiveJmxType = MethodType.methodType(jmxParam.getClass()).unwrap().returnType();

        if (jmxParam.getClass() != paramType && primitiveJmxType != paramType) {
            if (!jmxParam.getClass().equals(String.class)) {
                return Optional.empty();
            }

            return objectMapper.readValue(jmxParam.toString(), paramType);
        }
        return jmxParam;
    }

    private List<Method> getCandidateMethods(Object target, String actionName) throws NoSuchMethodException {
        final var candidates = new ArrayList<Method>();
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(actionName)) {
                candidates.add(method);
            }
        }

        if (candidates.isEmpty()) {
            throw new NoSuchMethodException("The " + actionName + " method doesn't exist in " + target.getClass());
        }
        return candidates;
    }

    private Object augmentResult(final Object result) throws JsonProcessingException {
        if (result == null) {
            return null;
        }

        if (result.getClass().isPrimitive()) {
            return result;
        }

        if (result.getClass().getPackageName().equals("java.lang")) {
            return result;
        }

        if (result instanceof Collection<?>) {
            return collectObjects(((Collection<?>) result));
        }

        if (result instanceof Map<?, ?>) {
            return collectObjects(((Map<?, ?>) result).entrySet());
        }

        return objectMapper.writeValueAsString(result);
    }

    private <T> List<String> collectObjects(Collection<T> collection) throws JsonProcessingException {
        var result = new ArrayList<String>(collection.size());
        for (T element : collection) {
            result.add(objectMapper.writeValueAsString(element));
        }
        return result;
    }
}
```
</details>

<details>
  <summary>MBeanUtils.java</summary>

```java

import javax.management.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class MBeanUtils {

    public static MBeanOperationInfo[] operations(final Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(MBeanUtils::getmBeanOperationInfo)
                .toArray(MBeanOperationInfo[]::new);
    }

    private static MBeanOperationInfo getmBeanOperationInfo(final Method m) {
        final var params = Arrays.stream(m.getParameters())
                .map(p -> new MBeanParameterInfo(p.getName(), getPrimitiveTypeOrString(p.getType()), p.getName()))
                .toArray(MBeanParameterInfo[]::new);

        return new MBeanOperationInfo(m.getName(),
                m.getName(),
                params,
                getPrimitiveTypeOrString(m.getReturnType()),
                MBeanOperationInfo.UNKNOWN);
    }

    private static String getPrimitiveTypeOrString(final Class<?> type) {
        if (type.isPrimitive()) {
            return type.getTypeName();
        }

        if (String.class.getPackage() == type.getPackage()) {
            return type.getTypeName();
        }

        return String.class.getName();
    }
}
```
</details>

# Задание для самостоятельной работы

В качестве задания для самостоятельной работы предлагается внедрить прокси объект типа `org.slf4j.Logger` во все поля Spring бинов, которые помечены аннотацией `@WithLogger`.

```java
@Service
public class MyService {
    @WithLogger
    private org.slf4j.Logger logger;

    void action() {
        logger.info("Action!");
    }
}
```