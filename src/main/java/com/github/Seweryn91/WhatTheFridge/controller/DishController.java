package com.github.Seweryn91.WhatTheFridge.controller;

import com.github.Seweryn91.WhatTheFridge.model.Dish;
import com.github.Seweryn91.WhatTheFridge.model.Ingredient;
import com.github.Seweryn91.WhatTheFridge.service.DishService;
import com.github.Seweryn91.WhatTheFridge.service.IngredientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DishController {

    @Autowired
    private DishService dishService;

    @Autowired
    private IngredientService ingredientService;

    @GetMapping("/dish/new")
    public String addNewDish(Model model) {
        model.addAttribute("dish", new Dish());
        model.addAttribute("ingredients", ingredientService.getAllIngredients());
        return "newdish";
    }

    @GetMapping("/dish/get/{id}")
    @SuppressWarnings("unchecked")
    public String getDish(@PathVariable("id") long id, Model model, HttpServletRequest request) {
        Dish dish = dishService.getDishById(id);
        model.addAttribute("dish", dish);
        List<Ingredient> allDishIngredients = dish.getIngredients().stream().sorted().toList();
        model.addAttribute("ingredients", allDishIngredients);

        if (request.getSession().getAttribute("set") != null) {
            HashSet<Ingredient> allSelectedIngredients = (HashSet<Ingredient>) request.getSession()
                    .getAttribute("set");

            List<Ingredient> allSelectedList = getAllSelectedList(allSelectedIngredients);
            model.addAttribute("storedIngredients", getListOfAvailableIngsForDish(dish, allSelectedList));
            Collection<Ingredient> missing = getListOfMissingIngsForDish(dish, allSelectedList);
            model.addAttribute("missing", missing);
        }
        return "dish";
    }

    /**
     * Returns a sorted list of Ingredients which were selected in order not to override equals() and hashCode().
     * @param allSelectedIngredients collection of Ingredients to be collected.
     * @return sorted list of ingredients.
     */
    private List<Ingredient> getAllSelectedList(Collection<Ingredient> allSelectedIngredients) {
        List<Ingredient> allSelectedList = new ArrayList<>();
        for (Ingredient i : allSelectedIngredients) {
            allSelectedList.add(ingredientService.getIngredientByName(i.getName()));
        }
        Collections.sort(allSelectedList);
        return allSelectedList;
    }

    Collection<Ingredient> getListOfAvailableIngsForDish(Dish dish, Collection<Ingredient> storedIngredients) {
        storedIngredients.retainAll(dish.getIngredients());
        return storedIngredients;
    }

    Collection<Ingredient> getListOfMissingIngsForDish(Dish dish,
                                                 Collection<Ingredient> storedIngs) {
        Collection<Ingredient> missingIngredients = new TreeSet<>(dish.getIngredients());
        missingIngredients.removeAll(storedIngs);
        return missingIngredients;
    }


    @PostMapping("dish/save/")
    public String saveDish( @ModelAttribute("Dish") Dish dish,
                            @RequestParam(value="ings", required = false) int[] ings,
                            BindingResult bindingResult) {

        if (ings != null) {
            Ingredient ingredient = null;
            for (int ing : ings) {
                if (ingredientService.isFound(ing)) {
                    ingredient = new Ingredient();
                    ingredient.setId((long) ing);
                    dish.addIngredient(ingredient);
                }
            }
        }
        dishService.saveOrUpdate(dish);
        return "redirect:/";
    }


    @GetMapping("/dish/delete/{id}")
    public String deleteDish(@PathVariable("id") long id) {
        dishService.deleteDishById(id);
        return "redirect:/";
    }

    @GetMapping("/dishes/all")
    public String showAllDishes(Model model) {
        model.addAttribute("alldishes", dishService.getDishList());
        return "all";
    }

    @GetMapping("/dish/update/{id}")
    public String updateDish(@PathVariable("id") long id, Model model) {
        Dish dish = dishService.getDishById(id);
        model.addAttribute("dish", dish);
        Set<Ingredient> dishIngredients = dishService.getIngredients(id);
        List<Ingredient> allIngredients = new ArrayList<>(ingredientService.getAllIngredients());
        getIngredientSetFromMap(getCheckedIngredientsMap(dishIngredients, allIngredients));
        model.addAttribute("map", getCheckedIngredientsMap(dishIngredients, allIngredients));
        return "updatedish";
    }

    void storeIngredientsInSession(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession();
        Set<Ingredient> selectedIngredients = Arrays.stream(request.getParameterValues("ingredient"))
                .map((s -> ingredientService.getIngredientByName(s))).collect(Collectors.toSet());
        session.setAttribute("set", selectedIngredients);
        model.addAttribute("set", selectedIngredients);
    }

    @GetMapping(value = "/dishes")
    String getDishesOfType(@RequestParam MultiValueMap<String, String> parameterMap,
                           @RequestParam(required = false) Optional<String> dishType,
                           Model model, HttpServletRequest request) {
        Set<String> ingredientsSet = new TreeSet<>();
        Set<Ingredient> selectedIngredients = new TreeSet<>();

        if (request.getParameterValues("ingredient") != null &&
                request.getParameterValues("ingredient").length > 0) {
            storeIngredientsInSession(request, model);
        }

        if (dishType.isEmpty() || dishType.get().equals("both")) {
            for (String key : parameterMap.keySet()) ingredientsSet.addAll(parameterMap.get(key));
            model.addAttribute("dishes", dishService.findByIngredientNamesIn(ingredientsSet));
        }
        if (dishType.isPresent() && dishType.get().equals("savory")) {
            for (String key : parameterMap.keySet()) ingredientsSet.addAll(parameterMap.get(key));
            model.addAttribute("dishes", dishService.findSavoryByIngredientNamesIn(ingredientsSet));
        }
        if (dishType.isPresent() && dishType.get().equals("sweet")) {
            for (String key : parameterMap.keySet()) ingredientsSet.addAll(parameterMap.get(key));
            model.addAttribute("dishes", dishService.findSweetByIngredientNamesIn(ingredientsSet));
        }

        return "dishes";
    }



    /**
     * Returns a Map of Ingredients which are checked (TRUE) if an Ingredient is present in the Dish.
     * @param dishIngredients collection of Ingredients that are contained in the field "ingredients" of Dish object
     * @param allIngredients collection of all Ingredient objects
     * @return map of ingredients contained in Dish
     */
    public Map<Ingredient, Boolean> getCheckedIngredientsMap(Collection<Ingredient> dishIngredients,
                                                             Collection<Ingredient> allIngredients) {
        Map<Ingredient, Boolean> ingredientsPresentInDish = new TreeMap<>();

        for (Ingredient ingredient : allIngredients) ingredientsPresentInDish.put(ingredient, false);

        for (Ingredient dishIngredient : dishIngredients) {
            for (Ingredient availableIngredient : allIngredients) {
                if (availableIngredient.equals(dishIngredient)) {
                    ingredientsPresentInDish.put(availableIngredient, true);
                }
            }
        }
        return ingredientsPresentInDish;
    }

    /**
     * Filters and returns Ingredients that are checked (TRUE) from map.
     * @param dishIngredientsMap map containing all Ingredients
     * @return set of checked (TRUE) Ingredients
     */
    public Set<Ingredient> getIngredientSetFromMap(Map<Ingredient, Boolean> dishIngredientsMap) {
        return dishIngredientsMap.entrySet().stream().filter(entry -> Objects.equals(entry.getValue(), true))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

}
