package org.springframework.security.boot;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.boot.biz.authentication.AuthenticatingFailureCounter;
import org.springframework.security.boot.biz.authentication.PostRequestAuthenticationProcessingFilter;
import org.springframework.security.boot.biz.authentication.captcha.CaptchaResolver;
import org.springframework.security.boot.ldap.authentication.LadpAuthenticationProcessingFilter;
import org.springframework.security.boot.ldap.authentication.LdapAuthenticationFailureHandler;
import org.springframework.security.boot.ldap.authentication.LdapAuthenticationSuccessHandler;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.ldap.authentication.AbstractLdapAuthenticationProvider;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@AutoConfigureBefore({ SecurityFilterAutoConfiguration.class })
@ConditionalOnClass({ AbstractSecurityWebApplicationInitializer.class, SessionCreationPolicy.class })
@ConditionalOnProperty(prefix = SecurityLdapProperties.PREFIX, value = "enabled", havingValue = "true")
@EnableConfigurationProperties({ SecurityLdapProperties.class, SecurityBizProperties.class })
public class SecurityLdapFilterConfiguration {

	@Configuration
	@ConditionalOnProperty(prefix = SecurityLdapProperties.PREFIX, value = "enabled", havingValue = "true")
	@EnableConfigurationProperties({ SecurityLdapProperties.class, SecurityBizProperties.class })
    @Order(107)
	static class JwtAuthcWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {
    	
    	private final AuthenticationManager authenticationManager;
	    private final ObjectMapper objectMapper;
	    private final RememberMeServices rememberMeServices;

	    private final SecurityBizProperties bizProperties;
		private final SecurityLdapProperties ldapProperties;
 	    private final AbstractLdapAuthenticationProvider authenticationProvider;
 	    private final LdapAuthenticationSuccessHandler authenticationSuccessHandler;
 	    private final LdapAuthenticationFailureHandler authenticationFailureHandler;
 	    private final CaptchaResolver captchaResolver;

		private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
		
		public JwtAuthcWebSecurityConfigurerAdapter(
				
				SecurityBizProperties bizProperties,
				SecurityLdapProperties ldapProperties,
				
				ObjectProvider<AuthenticationManager> authenticationManagerProvider,
   				ObjectProvider<ObjectMapper> objectMapperProvider,
   				ObjectProvider<SessionRegistry> sessionRegistryProvider,
   				ObjectProvider<RememberMeServices> rememberMeServicesProvider,
   				
   				ObjectProvider<AbstractLdapAuthenticationProvider> authenticationProvider,
   				ObjectProvider<LdapAuthenticationSuccessHandler> authenticationSuccessHandler,
   				ObjectProvider<LdapAuthenticationFailureHandler> authenticationFailureHandler,
   				ObjectProvider<CaptchaResolver> captchaResolverProvider,
   				
   				@Qualifier("jwtAuthenticatingFailureCounter") ObjectProvider<AuthenticatingFailureCounter> authenticatingFailureCounter,
				@Qualifier("jwtSessionAuthenticationStrategy") ObjectProvider<SessionAuthenticationStrategy> sessionAuthenticationStrategyProvider) {
		    
			this.bizProperties = bizProperties;
			this.ldapProperties = ldapProperties;
			
			this.authenticationManager = authenticationManagerProvider.getIfAvailable();
   			this.objectMapper = objectMapperProvider.getIfAvailable();
   			this.rememberMeServices = rememberMeServicesProvider.getIfAvailable();
   			
   			this.authenticationProvider = authenticationProvider.getIfAvailable();
   			this.authenticationSuccessHandler = authenticationSuccessHandler.getIfAvailable();
   			this.authenticationFailureHandler = authenticationFailureHandler.getIfAvailable();
   			this.captchaResolver = captchaResolverProvider.getIfAvailable();
   			
   			this.sessionAuthenticationStrategy = sessionAuthenticationStrategyProvider.getIfAvailable();
   			
		}

		@Bean
		public LadpAuthenticationProcessingFilter authenticationProcessingFilter() {
			
			// Form Login With LDAP 
			LadpAuthenticationProcessingFilter authenticationFilter = new LadpAuthenticationProcessingFilter(objectMapper, ldapProperties);
			
			/**
			 * 批量设置参数
			 */
			PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
			
			map.from(bizProperties.getSessionMgt().isAllowSessionCreation()).to(authenticationFilter::setAllowSessionCreation);
			
			map.from(authenticationManager).to(authenticationFilter::setAuthenticationManager);
			map.from(authenticationSuccessHandler).to(authenticationFilter::setAuthenticationSuccessHandler);
			map.from(authenticationFailureHandler).to(authenticationFilter::setAuthenticationFailureHandler);
			
			map.from(ldapProperties.getAuthc().isPostOnly()).to(authenticationFilter::setPostOnly);
			map.from(rememberMeServices).to(authenticationFilter::setRememberMeServices);
			map.from(sessionAuthenticationStrategy).to(authenticationFilter::setSessionAuthenticationStrategy);
			 
			return authenticationFilter;
		}
		
		@Override
		protected void configure(AuthenticationManagerBuilder auth) {
			// 配置LDAP的验证方式
			auth.authenticationProvider(authenticationProvider);
		}

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			http.addFilterBefore(authenticationProcessingFilter(), PostRequestAuthenticationProcessingFilter.class);
		}
		
		@Override
	    public void configure(WebSecurity web) throws Exception {
	    	web.ignoring().antMatchers(ldapProperties.getAuthc().getLoginUrlPatterns());
	    }
		
	}

	 

}
