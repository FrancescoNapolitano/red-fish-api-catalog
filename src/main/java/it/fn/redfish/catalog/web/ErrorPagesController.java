package it.fn.redfish.catalog.web;

import it.fn.redfish.catalog.support.I18n;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class ErrorPagesController implements ErrorController {

    @GetMapping("/error/403")
    public String forbidden(Model model) {
        model.addAttribute("statusCode", 403);
        model.addAttribute("statusText", I18n.text("message.access.denied"));
        model.addAttribute("statusDetail", I18n.text("message.you.do.not.have.permission.to.perform.this.operation"));
        return "error/status";
    }

    @RequestMapping("/error")
    public String handle(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int code = status instanceof Integer i ? i : 500;
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);

        model.addAttribute("statusCode", code);
        model.addAttribute("statusText", switch (code) {
            case 400 -> I18n.text("message.invalid.request");
            case 403 -> I18n.text("message.access.denied");
            case 404 -> I18n.text("message.page.not.found");
            case 405 -> I18n.text("message.method.not.allowed");
            case 413 -> I18n.text("message.file.too.large");
            default -> HttpStatus.resolve(code) != null ? HttpStatus.valueOf(code).getReasonPhrase() : I18n.text("message.error");
        });
        model.addAttribute("statusDetail", switch (code) {
            case 404 -> I18n.text("message.the.requested.address.does.not.exist.or.the.item.has.been.removed");
            case 403 -> I18n.text("message.you.do.not.have.permission.to.perform.this.operation");
            case 413 -> I18n.text("message.the.file.exceeds.the.maximum.allowed.size");
            default -> message == null || message.toString().isBlank()
                    ? I18n.text("message.an.unexpected.error.occurred") : message.toString();
        });
        return "error/status";
    }
}
