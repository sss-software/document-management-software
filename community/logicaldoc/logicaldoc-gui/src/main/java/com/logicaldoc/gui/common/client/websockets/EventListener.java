package com.logicaldoc.gui.common.client.websockets;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.SerializationStreamFactory;
import com.google.gwt.user.client.rpc.SerializationStreamReader;
import com.google.gwt.user.client.rpc.SerializationStreamWriter;
import com.logicaldoc.gui.common.client.Menu;
import com.logicaldoc.gui.common.client.Session;
import com.logicaldoc.gui.common.client.beans.GUIDocument;
import com.logicaldoc.gui.common.client.i18n.I18N;
import com.logicaldoc.gui.common.client.log.Log;
import com.logicaldoc.gui.common.client.observer.DocumentController;
import com.logicaldoc.gui.common.client.observer.FolderController;
import com.logicaldoc.gui.common.client.observer.UserController;
import com.logicaldoc.gui.common.client.widgets.PopupMessage;
import com.logicaldoc.gui.frontend.client.dashboard.chat.ChatController;
import com.sksamuel.gwt.websockets.WebsocketListener;

/**
 * Listens to events coming from websockets
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 8.1.1
 */
public class EventListener implements WebsocketListener {

	private static Set<String> moniteredEvents = new HashSet<String>();

	static {
		moniteredEvents.addAll(Arrays.asList("event.changed", "event.renamed", "event.checkedin", "event.checkedout",
				"event.locked", "event.unlocked", "event.immutable", "event.signed", "event.stamped",
				"event.password.protected", "event.password.unprotected", "event.stored", "event.moved",
				"event.deleted", "event.folder.renamed", "event.folder.changed", "event.folder.deleted",
				"event.folder.created", "event.folder.moved", "event.workflowstatus", "event.user.messagereceived",
				"event.chat.newmessage", "event.user.login", "event.user.logout", "event.user.timeout"));
	}

	/**
	 * Here there is the trick, the Async Service that is usual return by the
	 * deferred binding is also an instance of a SerializationStreamFactory.
	 * That can be used for serialize and deserialize objects
	 * 
	 * @param message the message to serialize
	 * 
	 * @return the message serialized in a string
	 */
	public String serializeMessage(EventMessage message) {
		try {
			SerializationStreamFactory factory = (SerializationStreamFactory) GWT
					.create(WebsocketsMessageService.class);
			SerializationStreamWriter writer = factory.createStreamWriter();
			writer.writeObject(message);
			final String data = writer.toString();
			return data;
		} catch (final SerializationException e) {
			Log.error(e.getMessage(), null, e);
		}
		return null;
	}

	public EventMessage deserializeMessae(String data) {
		try {
			SerializationStreamFactory factory = (SerializationStreamFactory) GWT
					.create(WebsocketsMessageService.class);
			final SerializationStreamReader streamReader = factory.createStreamReader(data);
			final EventMessage message = (EventMessage) streamReader.readObject();
			return message;
		} catch (final SerializationException e) {
			Log.error(e.getMessage(), null, e);
		}
		return null;
	}

	@Override
	public void onClose() {
		// do something on close
	}

	@Override
	public void onMessage(String msg) {
		onEvent(deserializeMessae(msg));
	}

	@Override
	public void onOpen() {
		// do something on open
	}

	private void onEvent(EventMessage event) {
		if (moniteredEvents.contains(event.getEvent())) {
			if ("event.changed".equals(event.getEvent()) || "event.renamed".equals(event.getEvent())
					|| "event.checkedin".equals(event.getEvent()) || "event.checkedout".equals(event.getEvent())
					|| "event.locked".equals(event.getEvent()) || "event.unlocked".equals(event.getEvent())
					|| "event.immutable".equals(event.getEvent()) || "event.signed".equals(event.getEvent())
					|| "event.stamped".equals(event.getEvent()) || "event.password.protected".equals(event.getEvent())
					|| "event.password.unprotected".equals(event.getEvent())
					|| "event.workflowstatus".equals(event.getEvent())) {
				if (Session.get().getCurrentFolder().getId() == event.getDocument().getFolder().getId())
					event.getDocument().setFolder(Session.get().getCurrentFolder());
				DocumentController.get().modified(event.getDocument());
			} else if ("event.stored".equals(event.getEvent())) {
				if (Session.get().getCurrentFolder().getId() == event.getDocument().getFolder().getId())
					event.getDocument().setFolder(Session.get().getCurrentFolder());
				DocumentController.get().stored(event.getDocument());
			} else if ("event.moved".equals(event.getEvent())) {
				if (Session.get().getCurrentFolder().getId() == event.getDocument().getFolder().getId())
					event.getDocument().setFolder(Session.get().getCurrentFolder());
				DocumentController.get().moved(event.getDocument());
			} else if ("event.deleted".equals(event.getEvent())) {
				DocumentController.get().deleted(new GUIDocument[] { event.getDocument() });
			} else if ("event.folder.renamed".equals(event.getEvent())
					|| "event.folder.changed".equals(event.getEvent())) {
				FolderController.get().modified(event.getFolder());
			} else if ("event.folder.deleted".equals(event.getEvent())) {
				FolderController.get().deleted(event.getFolder());
			} else if ("event.folder.created".equals(event.getEvent())) {
				FolderController.get().created(event.getFolder());
			} else if ("event.folder.moved".equals(event.getEvent())) {
				FolderController.get().moved(event.getFolder());
			} else if ("event.user.messagereceived".equals(event.getEvent()) && Menu.enabled(Menu.MESSAGES)) {
				if (event.getUserId() != null && event.getUserId() == Session.get().getUser().getId()) {
					Session.get().getUser().setUnreadMessages(Session.get().getUser().getUnreadMessages() + 1);
					UserController.get().changed(Session.get().getUser());
					String oneRow = event.getComment().replace('\n', ' ').replaceAll("<", "&lt;").replaceAll(">",
							"&gt;");
					Log.info(event.getAuthor() + ": "
							+ (oneRow.length() <= 80 ? oneRow : oneRow.substring(0, 80) + "..."));
					PopupMessage popup = new PopupMessage(I18N.message("newmessagefrom", event.getAuthor()),
							event.getComment());
					popup.show();
				}
			} else if ("event.user.login".equals(event.getEvent())) {
				UserController.get().loggedIn(event.getUsername());
			} else if ("event.user.logout".equals(event.getEvent()) || "event.user.timeout".equals(event.getEvent())) {
				UserController.get().loggedOut(event.getUsername());
			} else if ("event.chat.newmessage".equals(event.getEvent())) {
				ChatController.get().newMessage(event.getId(), event.getDate(), event.getUsername(),
						event.getComment());
			}
		}
	}
}