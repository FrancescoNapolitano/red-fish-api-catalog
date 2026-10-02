package it.fn.redfish.catalog.web;

import it.fn.redfish.catalog.support.I18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.support.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice(basePackages = "it.fn.redfish.catalog.web")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException e, HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("errorMessage", e.getMessage());
        return "redirect:" + referer(request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleUploadTooLarge(MaxUploadSizeExceededException e, HttpServletRequest request,
                                       RedirectAttributes flash) {
        flash.addFlashAttribute("errorMessage", I18n.text("message.the.file.exceeds.the.maximum.allowed.size"));
        return "redirect:" + referer(request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException e, org.springframework.ui.Model model) {
        model.addAttribute("statusCode", 403);
        model.addAttribute("statusText", I18n.text("message.access.denied"));
        model.addAttribute("statusDetail", e.getMessage() == null
                ? I18n.text("message.you.do.not.have.permission.to.perform.this.operation")
                : e.getMessage());
        return "error/status";
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NotFoundException e, org.springframework.ui.Model model) {
        model.addAttribute("statusCode", 404);
        model.addAttribute("statusText", I18n.text("message.item.not.found"));
        model.addAttribute("statusDetail", e.getMessage());
        return "error/status";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUnexpected(Exception e, org.springframework.ui.Model model) {
        log.error(I18n.text("message.unhandled.error"), e);
        model.addAttribute("statusCode", 500);
        model.addAttribute("statusText", I18n.text("message.internal.error"));
        model.addAttribute("statusDetail", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        return "error/status";
    }

    private String referer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return "/";
        }

        try {
            java.net.URI uri = java.net.URI.create(referer);
            String path = uri.getRawPath();
            String query = uri.getRawQuery();
            if (path == null || path.isBlank()) {
                return "/";
            }
            return query == null ? path : path + "?" + query;
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
