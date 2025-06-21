package org.lazywizard.console;
// Code taken and modified from console commands

import java.util.*;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatUIAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.lazylib.StringUtils;

public class CustomConsoleListeners {
    private static Logger logger = Global.getLogger(Console.class);

    public static class ConsoleCampaignListener implements CampaignInputListener, ConsoleListener  {
        @Override
        public int getListenerInputPriority() {
            return 9999;
        }
    
        @Override
        public void processCampaignInputPreCore(List<InputEventAPI> events) {
            // if (Global.getSector().getCampaignUI().isShowingMenu()) return;
    
            // if (Console.getSettings().getConsoleSummonKey().isPressed(events)) {
            //     ConsoleOverlay.show(getContext());
            //     events.clear();
            // }
    
            // Console.advance(this);
        }
    
        @Override
        public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {}
    
        @Override
        public void processCampaignInputPostCore(List<InputEventAPI> events) {}
    
        @Override
        public boolean showOutput(String output) {
            String[] lines = output.split("\n");
            for (String tmp : lines) {
                if (!tmp.isEmpty()) {
                    String message = StringUtils.wrapString(tmp, 100);
                    Global.getSector().getCampaignUI().addMessage(message, Console.getSettings().getOutputColor());
                }
            }

            logger.info(output);
            ConsoleOverlay.addToHistory(output);
            return true;
        }
    
        @Override
        public CommandContext getContext() {
            if (Global.getSector().getCampaignUI() != null &&
                Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null &&
                Global.getSector().getCampaignUI().getCurrentInteractionDialog().getInteractionTarget().getMarket() != null) {
                return CommandContext.CAMPAIGN_MARKET;
            }
            return CommandContext.CAMPAIGN_MAP;
        }
    }

    public static class ConsoleCombatListener extends BaseEveryFrameCombatPlugin implements ConsoleListener {
        private CommandContext context;
    
        @Override
        public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
            // if (context == null || Global.getCombatEngine().getPlayerShip() == null) return;
    
            // if (Console.getSettings().getConsoleSummonKey().isPressed(events)) {
            //     ConsoleOverlay.show(context);
            //     events.clear();
            // }
    
            // Console.advance(this);
        }
    
        @Override
        public void init(CombatEngineAPI engine) {
            if (engine.isSimulation()) {
                context = CommandContext.COMBAT_SIMULATION;
            } else if (engine.isInCampaign()) {
                context = CommandContext.COMBAT_CAMPAIGN;
            } else if (engine.getMissionId() != null) {
                context = CommandContext.COMBAT_MISSION;
            } else {
                context = CommandContext.MAIN_MENU;
            }
    
            engine.getCustomData().put("consolePlugin", this);
        }
    
        @Override
        public CommandContext getContext() {
            return context;
        }
    
        @Override
        public boolean showOutput(String output) {
            CombatUIAPI ui = Global.getCombatEngine().getCombatUI();
            if (ui == null) return false;

            String[] messages = output.split("\n");
            List<String> messageList = Arrays.asList(messages);
            Collections.reverse(messageList);
    
            for (String tmp : messageList) {
                String wrapped = StringUtils.wrapString(tmp, 80);
                ui.addMessage(0, Console.getSettings().getOutputColor(), wrapped);
            }

            logger.info(output);
            ConsoleOverlay.addToHistory(output);
            return true;
        }
    }
}
