package com.example.playground

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.ehcache.config.builders.{CacheConfigurationBuilder, CacheManagerBuilder, ResourcePoolsBuilder}
import org.ehcache.xml.XmlConfiguration

class EhcacheSpec extends WordSpecLike with Matchers with BeforeAndAfterAll {
  "Ehcache" should {
    "create cache instance programmatically" in {
      val cacheManager = CacheManagerBuilder.newCacheManagerBuilder
        .withCache(
          "preConfigured",
          CacheConfigurationBuilder
            .newCacheConfigurationBuilder(
              classOf[java.lang.Long],
              classOf[java.lang.String],
              ResourcePoolsBuilder.heap(10)
            )
        )
        .build
      cacheManager.init()

      val preConfigured = cacheManager.getCache("preConfigured", classOf[java.lang.Long], classOf[java.lang.String])

      val myCache = cacheManager.createCache(
        "myCache",
        CacheConfigurationBuilder
          .newCacheConfigurationBuilder(
            classOf[java.lang.Long],
            classOf[java.lang.String],
            ResourcePoolsBuilder.heap(10)
          )
      )

      myCache.put(1L, "da one!")
      val value = myCache.get(1L)

      cacheManager.removeCache("preConfigured")

      value should be("da one!")
    }

    "create cache instance with XML" in {
      val myUrl = getClass().getResource("/ehcache-config.xml");
      val xmlConfig = new XmlConfiguration(myUrl);
      val cacheManager = CacheManagerBuilder.newCacheManager(xmlConfig);
      cacheManager.init()

      val myCache = cacheManager.getCache("simpleCache", classOf[java.lang.Long], classOf[java.lang.String])

      myCache.put(1L, "da one!")
      val value = myCache.get(1L)

      cacheManager.removeCache("preConfigured")

      value should be("da one!")
    }
  }
}
