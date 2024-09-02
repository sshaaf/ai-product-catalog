""" Virtual Store Front Supervisor Agent

Agent that manages the virtual storefront by coordinating responses to customer
inquiries and deferral to other agents.
"""
import logging
import json
from typing import Literal
from langgraph.graph import END
from customer_greeter import qualify_customer_action
from sales_rep import clarify_customer_requirements_action
from semantic_search import product_semantic_search
import supervisor_state as ss

logger = logging.getLogger(__name__)

def qualify_customer(state: ss.CustomerVisitState):
    """ Qualify whether a new customer walking into the virtual store is interested in
        shoes.

        state - langchain graph state
    """
    logger.info("qualify_customer()")

    logger.debug("State=%s", state)
    user_message = state["messages"][0].content.strip()
    logger.info("User Message = %s", user_message)

    if qualify_customer_action(user_message):
        state["qualified_customer"] = "YES"
    else:
        state["qualified_customer"] = "NO"

    return state


def is_customer_qualified(state: ss.CustomerVisitState) -> \
                Literal["clarify_customer_requirements", END]:
    """ Determine whether the graph should proceed to clarify customer requirements or end.

        state - langgraph state
    """
    if state["qualified_customer"] != "YES":
        return END

    return "clarify_customer_requirements"


# pylint disable=W0718
def clarify_customer_requirements(state: ss.CustomerVisitState):
    """ Attempt to clarify customer purchasing needs node.

        state - langgraph state
    """
    logger.debug("clarify_customer_requirements")

    logger.debug("State=%s", state)
    user_message = state["messages"][0].content.strip()
    logger.info("User Message = %s", user_message)

    logger.debug ("Message History and Latest User Message prior to LLM>>  %s", state["messages"])

    if state["matching_products"] is not None:
        state["matching_products"].clear()

    response = clarify_customer_requirements_action(state["messages"])
    state["messages"].append(response)
    state["most_recent_ai_response"] = response

    try:
        response_json = json.loads(response.content)
        state["product_attributes"] = response_json["Attributes"]
    except Exception as e:
        logger.error("LLM produced unexpected response.  Exception=%s Response=%s",
                     e, response.content)
        state["product_attributes"] = ""
        if state["matching_products"] is not None:
            state["matching_products"].clear()

        print()

    return state


def is_sufficient_attributes(state: ss.CustomerVisitState) -> \
                Literal["match_attributes_to_product", END]:
    """ Check attributes to see if there is sufficient quanitity and quality to
        proceed.

        attributes - delimited string containing attribute data
    """
    if "product_attributes" in state:
        attributes = state["product_attributes"]
        if not isinstance(attributes, str) or len(attributes) == 0:
            return False

        alist = attributes.split(",")

        if len(alist) >= 2:
            return "match_attributes_to_product"

    return END


def match_attributes_to_product(state: ss.CustomerVisitState):
    """ Take the shoe attributes gathered from the customer and attempt to
        match available products to them.  """
    logger.debug("match_attributes_to_product")

    attributes = state["product_attributes"]
    logger.debug("Matching Attributes to Products via Semantic Search: %s", attributes)

    products = product_semantic_search(attributes, 3)
    logger.info("Matched Products!  Attributes=%s  Matching_Products=%s", attributes, products)
    state["matching_products"] = products

    return state
