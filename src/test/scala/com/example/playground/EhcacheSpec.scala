package com.example.playground

import java.io.File

import org.ehcache.config.builders.{CacheConfigurationBuilder, CacheManagerBuilder, ResourcePoolsBuilder}
import org.ehcache.config.units.MemoryUnit
import org.ehcache.xml.XmlConfiguration
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

trait Fruit
case object Apple extends Fruit
case object Banana extends Fruit
class Book(var isbn: String, fruit: Fruit) extends Serializable
case class ComplexBook(value: Option[Book])

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
      val value: String = myCache.get(1L)
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
        .`with`(CacheManagerBuilder.persistence(new File("/tmp", "scala-playground-ehcache")))
        .build
      cacheManager.init()

      val cache = cacheManager.createCache(
        "myCache",
        CacheConfigurationBuilder
          .newCacheConfigurationBuilder(
            classOf[String],
            classOf[ComplexBook],
            ResourcePoolsBuilder.heap(10).disk(10, MemoryUnit.MB, true)
          )
      )
      cache.put("1", ComplexBook(Some(new Book("id1", Apple))))
      cache.put("2", ComplexBook(Some(new Book("id2", Apple))))
      cache.put("3", ComplexBook(Some(new Book("id3", Apple))))
      cache.put("4", ComplexBook(Some(new Book("id4", Apple))))
      val value: ComplexBook = cache.get("1")
      value.value.get.isbn should be("id1")

      cacheManager.removeCache("myCache")
    }

  }
}
