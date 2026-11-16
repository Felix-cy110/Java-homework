import com.campus.reservation.common.BusinessException;
import com.campus.reservation.common.util.ObjectReflectUtil;
import com.campus.reservation.entity.*;
import com.campus.reservation.enums.ReserveStatus;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.*;

/** 独立验证通用工具，不依赖 DAO 或真实账号；测试模型不进入正式业务包。 */
public class ObjectReflectUtilTest {
    private static int checks;
    /** 继承字段应被三个工具统一识别。 */
    public static class Parent {
        private String parent;
        public String getParent() { return parent; }
        public void setParent(String value) { parent = value; }
    }
    /** 包含布尔属性、缩写属性、静态字段和引用的普通 Bean。 */
    public static class Bean extends Parent {
        private static String global = "static-sentinel";
        private String title;
        private String URL;
        private Boolean ready;
        private Integer count;
        private List<String> tags;
        public Bean() { }
        public String getTitle() { return title; }
        public void setTitle(String value) { title = value; }
        public String getURL() { return URL; }
        public void setURL(String value) { URL = value; }
        public Boolean isReady() { return ready; }
        public void setReady(Boolean value) { ready = value; }
        public Integer getCount() { return count; }
        public void setCount(Integer value) { count = value; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> value) { tags = value; }
        public String getGlobal() { return global; }
        public void setGlobal(String value) { global = value; }
        public static String getStaticValue() { return "not-an-instance"; }
    }
    /** DTO 用接口类型接收 String/Integer；不兼容的 URL 字段保持默认值。 */
    public static class Target extends Parent {
        private CharSequence title;
        private int count = 7;
        private Integer URL = 9;
        private Boolean ready;
        private List<String> tags;
        public Target() { }
        public void setTitle(CharSequence value) { title = value; }
        public void setCount(int value) { count = value; }
        public void setURL(Integer value) { URL = value; }
        public void setReady(Boolean value) { ready = value; }
        public void setTags(List<String> value) { tags = value; }
    }
    /** 同名 setter 重载优先选精确类型，不能依赖反射返回顺序。 */
    public static class Overloaded {
        private String selected;
        public Overloaded() { }
        public void setTitle(Object value) { selected = "object"; }
        public void setTitle(String value) { selected = "string"; }
    }
    /** getter 失败时按规范回退对象自己的 toString。 */
    public static class Broken {
        public String getValue() { throw new IllegalStateException("底层信息不应外泄"); }
        @Override public String toString() { return "fallback-value"; }
    }
    /** 连 toString 也失败时应仍返回稳定形态而非抛出异常。 */
    public static class BrokenString extends Broken {
        @Override public String toString() { throw new IllegalArgumentException("底层"); }
    }
    /** 无可访问构造器属于无法创建目标，不伪造成功结果。 */
    public static class NoConstructor {
        private NoConstructor() { }
    }
    /** 构造失败不能暴露原始消息或 cause。 */
    public static class ThrowConstructor {
        public ThrowConstructor() { throw new IllegalStateException("内部构造异常"); }
    }
    /** 一个 setter 抛出异常不影响其他属性的赋值。 */
    public static class BrokenSetter extends Parent {
        public BrokenSetter() { }
        public void setTitle(String title) { throw new IllegalArgumentException("内部赋值异常"); }
    }
    /** 一个 getter 抛出异常不影响其他属性的复制。 */
    public static class BrokenGetter extends Bean {
        @Override public String getTitle() { throw new IllegalStateException("内部读取异常"); }
    }
    /** 字段为空校验不得调用可能有副作用的 getter。 */
    public static class PrivateFields {
        private String value;
        public String getValue() { throw new IllegalStateException("不应调用"); }
    }
    /** 基本类型永不等于 null；静态字段不计入。 */
    public static class Primitive { private int value; }
    /** 递归对象用于确认序列化不会无限递归。 */
    public static class Node {
        private Node next;
        public Node getNext() { return next; }
    }
    /** 非公有类上的公有方法触发访问失败，用来验证 IllegalAccessException 降级。 */
    private static class Inaccessible {
        public String getValue() { return "hidden"; }
        @Override public String toString() { return "inaccessible-fallback"; }
    }
    /** 无 getter 对象使用原来的 toString；静态字段不能当作实例数据。 */
    public static class Plain {
        private static String value = "static-only";
        @Override public String toString() { return "plain-fallback"; }
    }
    /** 测试约束和典型边界，失败只显示断言名称。 */
    public static void main(String[] args) {
        check(Modifier.isPrivate(ObjectReflectUtil.class.getDeclaredConstructors()[0].getModifiers()), "私有构造");
        Arrays.stream(ObjectReflectUtil.class.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
                .forEach(m -> check(Modifier.isStatic(m.getModifiers()), "公有方法为静态"));
        check("".equals(ObjectReflectUtil.objectToString(null)), "null为空串");
        check("abc".equals(ObjectReflectUtil.objectToString("abc")), "直接文本");
        check("42".equals(ObjectReflectUtil.objectToString(42)), "直接数字");
        Bean bean = new Bean();
        check(ObjectReflectUtil.isAllFieldNull(bean), "空对象忽略静态");
        String empty = ObjectReflectUtil.objectToString(bean);
        check(empty.contains("title=null") && empty.contains("parent=null"), "空字段及继承getter");
        check(!empty.contains("global") && !empty.contains("class=") && !empty.contains("staticValue"), "忽略静态和getClass");
        bean.setTitle("测试");bean.setURL("demo");bean.setReady(false);bean.setCount(3);
        bean.setParent("父属性");bean.setTags(new ArrayList<>(Arrays.asList("one", "two")));
        String text=ObjectReflectUtil.objectToString(bean);
        check(text.contains("ready=false") && text.contains("URL=demo"), "布尔及缩写getter");
        check(text.contains("tags=[one, two]"), "集合序列化");
        check(text.equals(ObjectReflectUtil.objectToString(bean)), "输出顺序稳定");
        Target copy=ObjectReflectUtil.copyProperties(bean,Target.class);
        check("测试".contentEquals(copy.title) && copy.count==3 && !copy.ready, "兼容赋值与拆箱");
        check(copy.URL==9 && "父属性".equals(copy.getParent()), "不匹配跳过及继承setter");
        check(copy.tags==bean.getTags(), "浅拷贝引用");
        check("string".equals(ObjectReflectUtil.copyProperties(bean,Overloaded.class).selected), "重载精确类型");
        Bean same=ObjectReflectUtil.copyProperties(bean,Bean.class);
        check(same!=bean && text.equals(ObjectReflectUtil.objectToString(same)), "同类型复制");
        Target nulls=ObjectReflectUtil.copyProperties(new Bean(),Target.class);
        check(nulls.count==7 && nulls.title==null, "null不写基本类型");
        check(ObjectReflectUtil.copyProperties(null,Bean.class)==null, "空源返回null");
        check("父属性".equals(ObjectReflectUtil.copyProperties(bean,BrokenSetter.class).getParent()), "setter异常跳过");
        BrokenGetter brokenGetter=new BrokenGetter();brokenGetter.setParent("继续复制");
        check("继续复制".equals(ObjectReflectUtil.copyProperties(brokenGetter,Bean.class).getParent()), "getter异常跳过");
        expectFailure(() -> ObjectReflectUtil.copyProperties(bean,NoConstructor.class));
        expectFailure(() -> ObjectReflectUtil.copyProperties(bean,ThrowConstructor.class));
        expectFailure(() -> ObjectReflectUtil.copyProperties(bean,null));
        check("fallback-value".equals(ObjectReflectUtil.objectToString(new Broken())), "读取失败回退toString");
        check(ObjectReflectUtil.objectToString(new BrokenString()).contains("BrokenString@"), "toString异常二次降级");
        check(ObjectReflectUtil.isAllFieldNull(null) && ObjectReflectUtil.isAllFieldNull(new Object()), "空引用和无成员");
        PrivateFields fields=new PrivateFields();check(ObjectReflectUtil.isAllFieldNull(fields), "只读私有字段");
        fields.value="";check(!ObjectReflectUtil.isAllFieldNull(fields), "空字符串不是null");
        check(!ObjectReflectUtil.isAllFieldNull(new Primitive()), "基本类型0不是null");
        Bean inherited=new Bean();inherited.setParent("x");check(!ObjectReflectUtil.isAllFieldNull(inherited), "继承字段非空");
        Node node=new Node();node.next=node;check(ObjectReflectUtil.objectToString(node).contains("[循环引用]"), "循环对象保护");
        Map<String,Object> map=new LinkedHashMap<>();map.put("self",map);map.put("password","not-to-display");
        check(!ObjectReflectUtil.objectToString(map,"password").contains("not-to-display"), "嵌套敏感字典");
        check(ObjectReflectUtil.objectToString(map).contains("循环引用"), "循环容器保护");
        check("[1, 2]".equals(ObjectReflectUtil.objectToString(new int[]{1,2})), "基本类型数组");
        check("inaccessible-fallback".equals(ObjectReflectUtil.objectToString(new Inaccessible())), "方法不可访问降级");
        check("plain-fallback".equals(ObjectReflectUtil.objectToString(new Plain())), "无getter降级");
        check(ObjectReflectUtil.isAllFieldNull(new Plain()), "仅静态字段对象为空");
        Node head=new Node(), cursor=head;
        for(int i=0;i<40;i++){cursor.next=new Node();cursor=cursor.next;}
        check(ObjectReflectUtil.objectToString(head).contains("层级过深"), "深链保护");
        check(ObjectReflectUtil.isAllFieldNull(new User()) && ObjectReflectUtil.isAllFieldNull(new ReserveRecord())
                && ObjectReflectUtil.isAllFieldNull(new CampusResource()) && ObjectReflectUtil.isAllFieldNull(new OperationLogRecord()), "空实体字段均null");
        check(!ObjectReflectUtil.isAllFieldNull(new ResourceStatDTO()), "统计DTO基本计数默认0不等于null");
        LocalDateTime now=LocalDateTime.of(2026,11,16,12,0);
        Object[] entities={new User("demo","demo-secret","student",now),new CampusResource("r","教室","教室","AVAILABLE","A"),
            new ReserveRecord("b","r","demo",now,now.plusDays(1),now.plusDays(2),ReserveStatus.PENDING,"备注"),
            new OperationLogRecord("l","demo",now,"test.call","操作","入参","结果",""),
            new ResourceStatDTO("r","教室",2,1,1,0,0.5)};
        for(Object entity:entities) {
            String value=ObjectReflectUtil.objectToString(entity);
            check(value.startsWith(entity.getClass().getSimpleName()+"{"), "五类实体反射展示");
            check(!value.contains("class="), "排除getClass");
            Object cloned=ObjectReflectUtil.copyProperties(entity,entity.getClass());
            check(entity!=cloned && value.equals(ObjectReflectUtil.objectToString(cloned)), "五类实体属性拷贝");
            check(!ObjectReflectUtil.isAllFieldNull(entity), "五类实体判空");
        }
        check(!ObjectReflectUtil.objectToString(entities[0],"password").contains("demo-secret"), "敏感字段过滤");
        System.out.println("PASS ObjectReflectUtilTest: "+checks+" checks");
    }
    /** 不应向上层暴露反射异常链或原始异常消息。 */
    private static void expectFailure(Runnable action) {
        try { action.run();throw new AssertionError("应有友好提示"); }
        catch(BusinessException e) { check(e.getCause()==null && e.getMessage().startsWith("对象复制失败"), "友好业务提示"); }
    }
    /** 统一断言计数。 */
    private static void check(boolean value,String name) { if(!value)throw new AssertionError(name);checks++; }
}
