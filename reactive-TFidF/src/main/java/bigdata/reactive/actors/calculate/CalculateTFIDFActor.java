package bigdata.reactive.actors.calculate;

import akka.actor.*;
import akka.japi.pf.DeciderBuilder;
import akka.routing.ActorRefRoutee;
import akka.routing.RoundRobinRoutingLogic;
import akka.routing.Routee;
import akka.routing.Router;
import bigdata.reactive.messages.*;
import scala.concurrent.duration.Duration;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static akka.actor.SupervisorStrategy.restart;

public class CalculateTFIDFActor extends AbstractActor {

	private int total_messages;
	private int actual_messages = 0;

	// create supervisor strategy
	final SupervisorStrategy routingSupervisorStrategy =
			new OneForOneStrategy(
					10,
					Duration.create("10 seconds"),
					DeciderBuilder
							.match(RuntimeException.class, ex -> restart())
							.build()
			);

	// create the router to calculate term frequency
	Router router;

	{
		List<Routee> routees = new ArrayList<Routee>();
		for (int i = 0; i < 4; i++) {
			ActorRef r = getContext().actorOf(Props.create(TFIDFActor.class));
			getContext().watch(r);
			routees.add(new ActorRefRoutee(r));
		}
		router = new Router(new RoundRobinRoutingLogic(), routees);
	}

	ActorRef supervisor_actor;
	ActorRef aggregate_tfidf = getContext().actorOf(AggregateCellList.props(), "agg_tfidf");
	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(RequestCalculatorTFIDFMessage.class, msg ->{
					supervisor_actor = getSender();

					Map<String, Integer> terms = msg.getTerms_list();

					this.total_messages = terms.size();
					this.actual_messages = 0;

					for ( String t : terms.keySet() ){

						router.route(
								new RequestTFIDFMessage(t, msg.getTerm_frequency_list(),
								msg.getDocument_list(), msg.getInv_doc_list()),
								getSelf());

					}
				}).match(CellListMessage.class, msg ->{
					this.actual_messages++;
					aggregate_tfidf.tell(msg, getSelf());
					if ( actual_messages == total_messages )
						aggregate_tfidf.tell(new ResultRequest(), getSelf());
				}).match(TableData.class, msg->{
					supervisor_actor.tell(msg, getSelf());
				})
				.build();
	}

	public static Props props () {
		return Props.create(CalculateTFIDFActor.class);
	}


}
