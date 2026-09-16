package com.atguigu.gulimall.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.SumAggregate;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.Data;
import lombok.ToString;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

// 不需要 @EnableDiscoveryClient —— 它已加在主启动类上，
// @SpringBootTest 启动的就是那个类，这里再写一遍没有作用
@SpringBootTest
class GulimallSearchApplicationTests {

    @Autowired
    private ElasticsearchClient client;

    @Test
    void contextLoads() {
        System.out.println(client);
    }

    // GET users/_doc/1
    // DELETE users
    @Test
    void indexData() throws IOException {
        // 要保存的数据
        User user = new User();
        user.setUsername("张三");
        user.setAge(18);
        user.setGender("男");

        // 一行搞定：新客户端用 Lambda 式构建器，且自动把对象序列化成 JSON
        IndexResponse response = client.index(i -> i
                .index("users")     // 索引名
                .id("1")            // 文档 id
                .document(user)     // 文档内容，Jackson 自动序列化
        );

        System.out.println("result = " + response.result());
    }

    // 必须声明为 static：非静态内部类持有外部类引用，
    // Jackson 能序列化但无法反序列化（搜用 User.class 时会报 no default constructor）
    @Data
    static class User {
        private String username;
        private String gender;
        private Integer age;
    }

    /*
        PUT bank/_doc/99
        {"account_number":99,"balance":47159,"firstname":"Reyna","lastname":"Wallace","age":28,"gender":"M","address":"990 Mill Road","employer":"Rice","email":"reynawallace@rice.com","city":"Trona","state":"CA"}
        PUT bank/_doc/101
        {"account_number":101,"balance":15000,"firstname":"Ilene","lastname":"Vinson","age":35,"gender":"M","address":"210 Mill Street","employer":"Tersa","email":"ilenevinson@tersa.com","city":"Kirk","state":"MD"}
        DELETE bank
     */
    @Test
    void searchData() throws IOException {
        // 1、构造检索请求：查询 address 包含 mill 的，并做两个聚合
        //    直接在 search 的 Lambda 里链式写 DSL，与 Kibana 里写的 JSON 一一对应
        SearchResponse<Account> response = client.search(s -> s
                        .index("bank")
                        .query(q -> q.match(m -> m.field("address").query("mill")))
                        // 按照年龄的值分布进行聚合
                        .aggregations("ageAgg", a -> a.terms(t -> t.field("age").size(10)))
                        // 计算平均薪资
                        .aggregations("balanceAvg", a -> a.avg(avg -> avg.field("balance")))
                        // 计算总薪资
                        .aggregations("balanceSum", a -> a.sum(sum -> sum.field("balance"))),
                Account.class);   // 指定结果映射的实体类，新客户端自动反序列化

        // 2、获取所有查到的数据
        for (Hit<Account> hit : response.hits().hits()) {
            // hit.index() / hit.id() 可以拿基本属性
            System.out.println("account = " + hit.source());   // source() 直接就是对象
        }

        // 3、获取聚合信息
        //    新客户端的聚合结果是【强类型】的，访问器由【被聚合字段的类型】决定：
        //      keyword/text 字段 → .sterms()  key() 返回 FieldValue
        //      整数 字段        → .lterms()  key() 直接是 long
        //      小数 字段        → .dterms()  key() 直接是 double
        //    age 映射为 long，所以用 lterms()（用 sterms() 会抛 IllegalStateException）
        //    另外 key() 的类型随访问器变化，lterms 下没有 longValue() 方法
        var ageAgg = response.aggregations().get("ageAgg").lterms();
        for (var bucket : ageAgg.buckets().array()) {
            System.out.println("年龄为 " + bucket.key() + " 岁的人有 " + bucket.docCount() + " 人");
        }

        var balanceAvg = response.aggregations().get("balanceAvg").avg();
        System.out.println("平均薪资为：" + balanceAvg.value());
        SumAggregate balanceSum = response.aggregations().get("balanceSum").sum();
        System.out.println("总薪资为：" + balanceSum.value());
    }

    /*
        对应 Kibana 里的：
        GET bank/_search
        {
          "query": { "match_all": {} },
          "aggs": {
            "ageAgg": {
              "terms": { "field": "age", "size": 100 },
              "aggs": {
                "ageBalanceAvg": { "avg": { "field": "balance" } }
              }
            }
          },
          "size": 0
        }
     */
    @Test
    void searchSubAggData() throws IOException {
        SearchResponse<Account> response = client.search(s -> s
                        .index("bank")
                        .query(q -> q.matchAll(m -> m))
                        .size(0)                      // 不看文档明细，只要聚合结果
                        // 外层聚合：按年龄分组
                        .aggregations("ageAgg", a -> a
                                .terms(t -> t.field("age").size(100))
                                // 子聚合：写在 terms 构建器的【里面】，
                                // 表示"在每个分组内部再算"（对应 JSON 里 aggs 与 terms 并列）
                                .aggregations("ageBalanceAvg", sub -> sub
                                        .avg(avg -> avg.field("balance")))),
                Account.class);

        // 遍历每个年龄分组
        var ageAgg = response.aggregations().get("ageAgg").lterms();
        for (var bucket : ageAgg.buckets().array()) {
            System.out.println("年龄 " + bucket.key() + " 共 " + bucket.docCount() + " 人");

            // ⚠️ 子聚合的结果挂在【每个桶】上，不是挂在顶层 ——
            //    这是与普通聚合取值最大的区别（普通聚合走 response.aggregations().get(...)）
            var ageBalanceAvg = bucket.aggregations().get("ageBalanceAvg").avg();
            System.out.println("  该年龄平均薪资：" + ageBalanceAvg.value());
        }
    }

    @Data
    @ToString
    public static class Account {
        private int account_number;
        private int balance;
        private String firstname;
        private String lastname;
        private int age;
        private String gender;
        private String address;
        private String employer;
        private String email;
        private String city;
        private String state;
    }

}
