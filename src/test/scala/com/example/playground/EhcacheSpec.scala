package com.example.playground

import java.io.File

import org.ehcache.config.builders.{CacheConfigurationBuilder, CacheManagerBuilder, ResourcePoolsBuilder}
import org.ehcache.config.units.MemoryUnit
import org.ehcache.xml.XmlConfiguration
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

case class Book(isbn: String)

class EhcacheSpec extends WordSpecLike with Matchers with BeforeAndAfterAll {
  "Ehcache" should {
    "create cache instance programmatically" in {
      val cacheManager = CacheManagerBuilder.newCacheManagerBuilder.build
      cacheManager.init()

      val myCache = cacheManager.createCache(
        "myCache",
        CacheConfigurationBuilder
          .newCacheConfigurationBuilder(classOf[java.lang.Long], classOf[String], ResourcePoolsBuilder.heap(10))
      )
      myCache.put(1L, "da one!")
      val value = myCache.get(1L)
      value should be("da one!")

      cacheManager.removeCache("myCache")
    }

    "create cache instance with XML" in {
      val myUrl = getClass().getResource("/ehcache-config.xml");
      val xmlConfig = new XmlConfiguration(myUrl);
      val cacheManager = CacheManagerBuilder.newCacheManager(xmlConfig);
      cacheManager.init()

      val myCache = cacheManager.getCache("simpleCache", classOf[java.lang.Long], classOf[String])
      myCache.put(1L, "da one!")
      val value = myCache.get(1L)
      value should be("da one!")

      cacheManager.removeCache("myCache")
    }

    "serialize and deserialize scala case class" in {
      val cacheManager = CacheManagerBuilder
        .newCacheManagerBuilder()
        .`with`(CacheManagerBuilder.persistence(new File("/tmp", "myData")))
        .build
      cacheManager.init()

      val cache = cacheManager.createCache(
        "myCache",
        CacheConfigurationBuilder
          .newCacheConfigurationBuilder(
            classOf[String],
            classOf[Book],
            ResourcePoolsBuilder.heap(10).disk(10, MemoryUnit.MB)
          )
      )
      cache.put("1", Book("id1"))
      cache.put("2", Book("id2"))
      cache.put("3", Book("id3"))
      cache.put("4", Book("id4"))
      val value = cache.get("1")
      value should be(Book("id1"))

      cacheManager.removeCache("myCache")
    }
  }
}
