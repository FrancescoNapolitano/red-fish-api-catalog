package it.fn.redfish.catalog.web;

import it.fn.redfish.catalog.support.I18n;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;

import it.fn.redfish.catalog.service.SetupService;
import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.web.form.SetupForm;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/setup")
@SessionAttributes("setupForm")
public class SetupController {

    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @ModelAttribute("setupForm")
    public SetupForm setupForm() {
        return new SetupForm();
    }

    @GetMapping
    public String start(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int step, Model model) {
        model.addAttribute("step", Math.max(1, Math.min(step, 2)));
        model.addAttribute("firstRun", setupService.isFirstRun());
        return "setup/wizard";
    }

    @PostMapping("/step1")
    public String step1(@Validated @ModelAttribute("setupForm") SetupForm form, BindingResult binding, Model model) {
        if (!form.passwordsMatch()) {
            binding.rejectValue("adminPasswordConfirm", "mismatch", I18n.text("message.the.passwords.do.not.match"));
        }
        if (binding.hasErrors()) {
            model.addAttribute("step", 1);
            return "setup/wizard";
        }
        model.addAttribute("step", 2);
        return "setup/wizard";
    }

    @PostMapping("/back")
    public String back(@ModelAttribute("setupForm") SetupForm form,
                       @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int step,
                       Model model) {
        model.addAttribute("step", Math.max(1, Math.min(step, 2)));
        return "setup/wizard";
    }

    @PostMapping("/complete")
    public String complete(@Valid @ModelAttribute("setupForm") SetupForm form, BindingResult binding,
                           SessionStatus sessionStatus, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("step", 1);
            return "setup/wizard";
        }
        try {
            setupService.complete(form);
        } catch (BusinessException e) {
            model.addAttribute("step", 2);
            model.addAttribute("errorMessage", e.getMessage());
            return "setup/wizard";
        }
        String username = form.getAdminUsername();
        sessionStatus.setComplete();
        return "redirect:/login?setup=done&username=" + username;
    }

}
