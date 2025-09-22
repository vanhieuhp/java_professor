package dev.hieunv.uploadfile;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootController {

    @GetMapping("/")
    public String redirectRootToFiles() {
        return "redirect:/files/";
    }
}


