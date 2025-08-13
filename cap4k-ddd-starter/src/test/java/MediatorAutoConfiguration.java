//import lombok.RequiredArgsConstructor;
//import org.netcorepal.cap4j.ddd.application.*;
//import org.netcorepal.cap4j.ddd.impl.DefaultMediator;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
//import org.springframework.context.ApplicationContext;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
/// **
// * CQS自动配置类
// *
// * @author binking338
// * @date 2024/8/24
// */
//@Configuration
//@RequiredArgsConstructor
//public class MediatorAutoConfiguration {
//
//    @Bean
//    @ConditionalOnMissingBean(Mediator.class)
//    public DefaultMediator defaultMediator(ApplicationContext applicationContext) {
//        DefaultMediator defaultMediator = new DefaultMediator();
//        MediatorSupport.configure(defaultMediator);
//        MediatorSupport.configure(applicationContext);
//        return defaultMediator;
//    }
//
//}
