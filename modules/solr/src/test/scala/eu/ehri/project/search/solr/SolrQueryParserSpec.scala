package eu.ehri.project.search.solr

import helpers.ResourceUtils
import play.api.test.PlaySpecification
import play.api.i18n.Messages
import utils.search.FieldFacetClass
import views.Helpers
import models.base.Description


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class SolrQueryParserSpec extends PlaySpecification with ResourceUtils {

  private def xmlResponseString: String = resourceAsString("solrQueryResponse1.xml")
  private def jsonResponseString: String = resourceAsString("solrQueryResponse1.json")

  "Solr XML Query Parser" should {
    "parse the correct number of docs with the right IDs" in {
      val qp = XmlResponseHandler.getResponseParser(xmlResponseString)
      val docs = qp.items
      docs.size must equalTo(2)
      docs.find(d => d.itemId == "ehri-cb-638") must beSome
      docs.find(d => d.itemId == "nl") must beSome
    }

    "parse facets correctly" in {
      val qp = XmlResponseHandler.getResponseParser(xmlResponseString)
      val allFacets = List(
        FieldFacetClass(
          key=Description.LANG_CODE,
          name=Messages("documentaryUnit." + Description.LANG_CODE),
          param="lang",
          render=Helpers.languageCodeToName
        )
      )
      val facetData = qp.extractFacetData(List.empty, allFacets)
      facetData.size must equalTo(1)
      facetData(0).count must equalTo(2)
      facetData(0).facets(0).value must equalTo("eng")
      facetData(0).facets(1).value must equalTo("fre")
      facetData(0).sortedByCount.headOption must beSome.which { top =>
        top.value must equalTo("eng")
      }
    }

    "parse highlighting correctly" in {
      val qp = XmlResponseHandler.getResponseParser(xmlResponseString)
      val highlightMap: Map[String, Map[String, Seq[String]]] = qp.highlightMap
      highlightMap.size must equalTo(2)
      val itemMap = highlightMap.get("ehri-cb-638-eng-292")
      itemMap must beSome
      val place = itemMap.get.get("place_t")
      place must beSome
      place.get must equalTo(Seq("Active in the Netherlands, <em>Amsterdam</em>."))

      val doc1 = qp.items(0)
      doc1.highlights.get("place_t").get must equalTo(Seq("Active in the Netherlands, <em>Amsterdam</em>."))
    }
  }

  "Solr JSON Query Parser" should {
    "parse the correct number of docs with the right IDs" in {
      val qp = JsonResponseHandler.getResponseParser(jsonResponseString)
      println(qp.count)
      val docs = qp.items
      docs.size must equalTo(2)
      docs.find(d => d.itemId == "ehri-cb-638") must beSome
      docs.find(d => d.itemId == "nl") must beSome
    }

    "parse facets correctly" in {
      val qp = JsonResponseHandler.getResponseParser(jsonResponseString)
      val allFacets = List(
        FieldFacetClass(
          key=Description.LANG_CODE,
          name=Messages("documentaryUnit." + Description.LANG_CODE),
          param="lang",
          render=Helpers.languageCodeToName
        )
      )
      val facetData = qp.extractFacetData(List.empty, allFacets)
      facetData.size must equalTo(1)
      facetData(0).count must equalTo(2)
      facetData(0).facets(0).value must equalTo("eng")
      facetData(0).facets(1).value must equalTo("fre")
      facetData(0).sortedByCount.headOption must beSome.which { top =>
        top.value must equalTo("eng")
      }
    }

    "parse highlighting correctly" in {
      val qp = JsonResponseHandler.getResponseParser(jsonResponseString)
      val highlightMap: Map[String, Map[String, Seq[String]]] = qp.highlightMap
      highlightMap.size must equalTo(2)
      val itemMap = highlightMap.get("ehri-cb-638-eng-292")
      itemMap must beSome
      val place = itemMap.get.get("place_t")
      place must beSome
      place.get must equalTo(Seq("Active in the Netherlands, <em>Amsterdam</em>."))

      val doc1 = qp.items(0)
      doc1.highlights.get("place_t").get must equalTo(Seq("Active in the Netherlands, <em>Amsterdam</em>."))
    }
  }
}
